package io.github.projectunified.craftcommand.processor.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import io.github.projectunified.craftcommand.processor.TypeNames;
import com.palantir.javapoet.TypeSpec;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Generates the tab-completion surface of a command wrapper: the suggestion routing of each command class and
 * one helper per completable argument.
 *
 * <p>The routing walks the declared arguments in order and returns the suggestions of the argument the sender is
 * currently typing, so a command with no suggestions costs a single {@code emptyList()} return.
 */
final class SuggestionGenerator {
    private static final ClassName COMMAND_MANAGER = ClassName.get("io.github.projectunified.craftcommand", "CommandManager");

    private final BaseCommandGenerator processor;

    SuggestionGenerator(BaseCommandGenerator processor) {
        this.processor = processor;
    }

    public boolean isParamDirectSuggestionAvailable(ParameterModel p) {
        if (p.getSuggestProvider() != null) return true;
        String resolveName = p.getResolveName();
        if (resolveName != null && !resolveName.isEmpty()) {
            return false; // Resolver params generate their own suggestions
        }
        return hasTypeSuggestionSource(p);
    }

    public boolean isResolverParamSuggestionAvailable(ParameterModel p) {
        return p.getSuggestProvider() != null || hasTypeSuggestionSource(p);
    }

    private boolean hasTypeSuggestionSource(ParameterModel p) {
        TypeName typeName = TypeName.get(p.getType());
        return TypeSupport.isBooleanType(typeName)
                || processor.typeSupport().hasPlatformSuggestions(typeName)
                || !processor.typeSupport().isBuiltIn(typeName);
    }

    public boolean isParamSuggestionAvailable(ParameterModel p, MethodModel commandMethod) {
        if (p.getSuggestProvider() != null) return true;
        TypeName typeName = TypeName.get(p.getType());
        if (TypeSupport.isBooleanType(typeName)) return true;
        if (processor.typeSupport().hasPlatformSuggestions(typeName)) return true;

        MethodModel resolverModel = p.getResolverMethod();
        if (resolverModel != null) {
            for (ParameterModel rp : resolverModel.getParameters()) {
                if (processor.isSenderParam(TypeName.get(rp.getType()), commandMethod)) continue;
                if (isParamSuggestionAvailable(rp, commandMethod)) return true;
            }
            return false;
        }

        return !processor.typeSupport().isBuiltIn(typeName);
    }

    /**
     * Whether this command class or its default action can offer suggestions.
     */
    boolean hasSuggestions(CommandModel model) {
        if (!model.getSubcommands().isEmpty() || !model.getNestedSubcommands().isEmpty()) return true;
        return model.getDefaultMethod() != null && hasAnySuggestions(model.getDefaultMethod());
    }

    public boolean hasAnySuggestions(MethodModel method) {
        for (ParameterModel p : method.getParameters()) {
            if (p == method.getSenderParameter()) continue;
            if (isParamSuggestionAvailable(p, method)) return true;
        }
        return false;
    }

    public void buildSuggestionRouting(MethodSpec.Builder methodSpec, CommandModel model, String argsVar, CommandModel rootModel) {
        boolean hasChildren = !model.getSubcommands().isEmpty() || !model.getNestedSubcommands().isEmpty();

        if (hasChildren) {
            List<String> names = BaseCommandGenerator.subcommandNames(model);
            String helperCall = null;
            MethodModel defaultMethod = model.getDefaultMethod();
            if (defaultMethod != null && !defaultMethod.getParameters().isEmpty()
                    && isParamDirectSuggestionAvailable(defaultMethod.getParameters().get(0))) {
                helperCall = getParameterSuggestionMethodName(model, defaultMethod, 0);
            }

            CodeBlock current = CodeBlock.of("$L[0]", argsVar);
            methodSpec.beginControlFlow("if ($L.length == 1)", argsVar);
            if (helperCall == null) {
                methodSpec.addStatement("return $T.filterSuggestions($L, $L)",
                        COMMAND_MANAGER, BaseCommandGenerator.stringList(names), current);
            } else {
                methodSpec.addStatement("$T<String> suggestions = new $T<>($L)", List.class, ArrayList.class, BaseCommandGenerator.stringList(names));
                methodSpec.addStatement("suggestions.addAll($L(sender, $L))", helperCall, argsVar);
                methodSpec.addStatement("return $T.filterSuggestions(suggestions, $L)", COMMAND_MANAGER, current);
            }
            methodSpec.endControlFlow();

            // Routing for args.length > 1
            boolean hasSubSuggestions = model.getNestedSubcommands().stream().anyMatch(this::hasSuggestions)
                    || model.getSubcommands().stream().anyMatch(sub -> hasAnySuggestions(sub));

            if (hasSubSuggestions) {
                methodSpec.beginControlFlow("if ($L.length > 1)", argsVar);
                methodSpec.addStatement("$T subArgs = $T.copyOfRange($L, 1, $L.length)", String[].class, Arrays.class, argsVar, argsVar);
                methodSpec.beginControlFlow("switch ($L[0].toLowerCase())", argsVar);

                // Route to nested subcommand classes
                for (CommandModel child : model.getNestedSubcommands()) {
                    if (!hasSuggestions(child)) continue;
                    BaseCommandGenerator.beginCaseBlock(methodSpec, BaseCommandGenerator.collectLoweredNames(child));
                    String childHelperName = Naming.suggestHelper(child.getClassName());
                    methodSpec.addStatement("return $L(sender, subArgs)", childHelperName);
                    methodSpec.endControlFlow();
                }

                // Route to subcommand methods ONLY IF THEY HAVE SUGGESTIONS
                for (MethodModel sub : model.getSubcommands()) {
                    if (!hasAnySuggestions(sub)) {
                        continue;
                    }
                    BaseCommandGenerator.beginCaseBlock(methodSpec, BaseCommandGenerator.collectLoweredNames(sub));
                    buildSubcommandSuggestionRouting(methodSpec, model, sub, "subArgs", false);
                    methodSpec.endControlFlow();
                }

                methodSpec.endControlFlow(); // switch
                methodSpec.endControlFlow(); // if
            }
        }

        // Default command tab complete
        if (model.getDefaultMethod() != null && hasAnySuggestions(model.getDefaultMethod())) {
            buildSubcommandSuggestionRouting(methodSpec, model, model.getDefaultMethod(), argsVar, model == rootModel);
        } else {
            methodSpec.addStatement("return $T.emptyList()", Collections.class);
        }
    }

    private void buildSubcommandSuggestionRouting(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, String argsVar, boolean argsMayBeEmpty) {
        List<SuggestionSlot> slots = suggestionSlots(classModel, method);
        if (slots.isEmpty()) {
            methodSpec.addStatement("return $T.emptyList()", Collections.class);
            return;
        }

        if (argsMayBeEmpty) {
            methodSpec.addStatement("if ($L.length == 0) return $T.emptyList()", argsVar, Collections.class);
        }

        if (slots.stream().allMatch(SuggestionSlot::hasStaticWidth)) {
            // Every width is known, so the checked bound is a constant and no accumulator is needed.
            int bound = 0;
            for (SuggestionSlot slot : slots) {
                bound += slot.staticWidth;
                if (slot.helper != null) {
                    methodSpec.addStatement("if ($L.length <= $L) return $L(sender, $L)",
                            argsVar, bound, slot.helper, argsVar);
                }
            }
        } else if (slots.size() == 1) {
            SuggestionSlot slot = slots.get(0);
            if (slot.helper != null) {
                methodSpec.addStatement("if ($L.length <= $L) return $L(sender, $L)",
                        argsVar, slot.width, slot.helper, argsVar);
            }
        } else {
            boolean needsAccumulator = false;
            for (int i = 1; i < slots.size(); i++) {
                if (slots.get(i).helper != null) {
                    needsAccumulator = true;
                    break;
                }
            }
            if (needsAccumulator) methodSpec.addStatement("int tempIdx = 0");
            for (int i = 0; i < slots.size(); i++) {
                SuggestionSlot slot = slots.get(i);
                if (slot.helper != null) {
                    if (needsAccumulator) {
                        methodSpec.addStatement("if ($L.length <= tempIdx + $L) return $L(sender, $L)",
                                argsVar, slot.width, slot.helper, argsVar);
                    } else {
                        methodSpec.addStatement("if ($L.length <= $L) return $L(sender, $L)",
                                argsVar, slot.width, slot.helper, argsVar);
                    }
                }
                if (needsAccumulator && hasHelperAfter(slots, i)) {
                    methodSpec.addStatement("tempIdx += $L", slot.width);
                }
            }
        }
        methodSpec.addStatement("return $T.emptyList()", Collections.class);
    }

    private static boolean hasHelperAfter(List<SuggestionSlot> slots, int index) {
        for (int i = index + 1; i < slots.size(); i++) {
            if (slots.get(i).helper != null) return true;
        }
        return false;
    }

    /**
     * Lists the argument slots of a command method that can be completed, in argument order.
     */
    private List<SuggestionSlot> suggestionSlots(CommandModel classModel, MethodModel method) {
        List<SuggestionSlot> slots = new ArrayList<>();
        for (int i = 0; i < method.getParameters().size(); i++) {
            ParameterModel p = method.getParameters().get(i);
            MethodModel resolverModel = p.getResolverMethod();

            if (resolverModel != null) {
                ExecutableElement localResolver = resolverModel.getElement();
                int resolverStartIndex = processor.firstParamIsSender(localResolver, method) ? 1 : 0;
                List<ParameterModel> resolverParams = resolverModel.getParameters();
                for (int ri = resolverStartIndex; ri < resolverParams.size(); ri++) {
                    if (isResolverParamSuggestionAvailable(resolverParams.get(ri))) {
                        String helper = getResolverParamSuggestionMethodName(classModel, method, p.getResolveName(), ri - resolverStartIndex);
                        slots.add(SuggestionSlot.staticWidth(helper, 1));
                    } else {
                        slots.add(SuggestionSlot.staticWidth(null, 1));
                    }
                }
            } else {
                TypeName pTypeName = TypeName.get(p.getType());
                String helper = isParamDirectSuggestionAvailable(p)
                        ? getParameterSuggestionMethodName(classModel, method, i)
                        : null;
                if (processor.typeSupport().isBuiltIn(pTypeName)) {
                    slots.add(SuggestionSlot.staticWidth(helper, processor.getBuiltInWidth(pTypeName)));
                } else {
                    TypeName boxedType = pTypeName.isPrimitive() ? pTypeName.box() : pTypeName;
                    slots.add(SuggestionSlot.dynamicWidth(helper,
                            CodeBlock.of("manager.getResolver($T.class).getWidth()", boxedType)));
                }
            }
        }
        return slots;
    }

    /**
     * One completable argument slot of a command method: the suggestion helper to call, if the slot offers
     * suggestions at all, and the number of arguments it spans.
     */
    private static final class SuggestionSlot {
        private final String helper;
        private final CodeBlock width;
        private final int staticWidth;

        private SuggestionSlot(String helper, CodeBlock width, int staticWidth) {
            this.helper = helper;
            this.width = width;
            this.staticWidth = staticWidth;
        }

        private static SuggestionSlot staticWidth(String helper, int width) {
            return new SuggestionSlot(helper, CodeBlock.of("$L", width), width);
        }

        private static SuggestionSlot dynamicWidth(String helper, CodeBlock width) {
            return new SuggestionSlot(helper, width, -1);
        }

        private boolean hasStaticWidth() {
            return staticWidth > 0;
        }
    }

    void buildParameterSuggestions(TypeSpec.Builder typeSpec, CommandModel model, CommandModel rootModel) {
        if (model.getDefaultMethod() != null) {
            for (int i = 0; i < model.getDefaultMethod().getParameters().size(); i++) {
                ParameterModel p = model.getDefaultMethod().getParameters().get(i);
                if (isParamDirectSuggestionAvailable(p)) {
                    typeSpec.addMethod(buildParameterSuggestionHelper(model, model.getDefaultMethod(), p, i, rootModel));
                }
                buildResolverParamSuggestions(typeSpec, model, model.getDefaultMethod(), p, rootModel);
            }
        }
        for (MethodModel sub : model.getSubcommands()) {
            for (int i = 0; i < sub.getParameters().size(); i++) {
                ParameterModel p = sub.getParameters().get(i);
                if (isParamDirectSuggestionAvailable(p)) {
                    typeSpec.addMethod(buildParameterSuggestionHelper(model, sub, p, i, rootModel));
                }
                buildResolverParamSuggestions(typeSpec, model, sub, p, rootModel);
            }
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            buildParameterSuggestions(typeSpec, child, rootModel);
        }
    }

    private void buildResolverParamSuggestions(TypeSpec.Builder typeSpec, CommandModel classModel, MethodModel method, ParameterModel p, CommandModel rootModel) {
        MethodModel resolverModel = p.getResolverMethod();
        if (resolverModel == null) return;

        int startIndex = processor.firstParamIsSender(resolverModel.getElement(), method) ? 1 : 0;
        for (int i = startIndex; i < resolverModel.getParameters().size(); i++) {
            ParameterModel rp = resolverModel.getParameters().get(i);
            if (isResolverParamSuggestionAvailable(rp)) {
                String helperName = getResolverParamSuggestionMethodName(classModel, method, p.getResolveName(), i - startIndex);
                typeSpec.addMethod(buildResolverParamSuggestionHelper(classModel, method, rp, helperName, rootModel));
            }
        }
    }

    /**
     * Generates the suggestion helpers of parameters whose suggestions come from their type (boolean or platform
     * built-in). Platforms that inline custom suggestion providers use this instead of the array-based helpers.
     */
    void buildTypeSuggestionHelpers(TypeSpec.Builder typeSpec, CommandModel model, CommandModel rootModel) {
        if (model.getDefaultMethod() != null) {
            buildTypeSuggestionHelpers(typeSpec, model, model.getDefaultMethod(), rootModel);
        }
        for (MethodModel sub : model.getSubcommands()) {
            buildTypeSuggestionHelpers(typeSpec, model, sub, rootModel);
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            buildTypeSuggestionHelpers(typeSpec, child, rootModel);
        }
    }

    private void buildTypeSuggestionHelpers(TypeSpec.Builder typeSpec, CommandModel classModel, MethodModel method, CommandModel rootModel) {
        for (int i = 0; i < method.getParameters().size(); i++) {
            ParameterModel p = method.getParameters().get(i);
            if (hasTypeSuggestions(p)) {
                typeSpec.addMethod(buildParameterSuggestionHelper(classModel, method, p, i, rootModel));
            }
            MethodModel resolverModel = p.getResolverMethod();
            if (resolverModel == null) continue;

            int startIndex = processor.firstParamIsSender(resolverModel.getElement(), method) ? 1 : 0;
            for (int j = startIndex; j < resolverModel.getParameters().size(); j++) {
                ParameterModel rp = resolverModel.getParameters().get(j);
                if (hasTypeSuggestions(rp)) {
                    typeSpec.addMethod(buildResolverParamSuggestionHelper(classModel, method, rp,
                            getResolverParamSuggestionMethodName(classModel, method, p.getResolveName(), j - startIndex), rootModel));
                }
            }
        }
    }

    private boolean hasTypeSuggestions(ParameterModel p) {
        if (p.getSuggestProvider() != null || p.getResolverMethod() != null) return false;
        TypeName typeName = TypeName.get(p.getType());
        return TypeSupport.isBooleanType(typeName) || processor.typeSupport().hasPlatformSuggestions(typeName);
    }

    String getParameterSuggestionMethodName(CommandModel classModel, MethodModel method, int index) {
        String methodOrDefault = method.isDefault() ? "default" : method.getSubcommandName();
        return Naming.suggestMethod(classModel.getClassName(), methodOrDefault, index);
    }

    String getResolverParamSuggestionMethodName(CommandModel classModel, MethodModel method, String resolverName, int index) {
        String methodOrDefault = method.isDefault() ? "default" : method.getSubcommandName();
        return Naming.suggestMethod(classModel.getClassName(), methodOrDefault + " " + resolverName, index);
    }

    private MethodSpec buildResolverParamSuggestionHelper(CommandModel classModel, MethodModel method, ParameterModel rp, String helperName, CommandModel rootModel) {
        return buildSuggestionHelperInternal(classModel, method, rp,
                processor.getInstanceVarExpression(classModel, rootModel), helperName, rootModel);
    }

    private MethodSpec buildParameterSuggestionHelper(CommandModel classModel, MethodModel method, ParameterModel p, int index, CommandModel rootModel) {
        String helperName = getParameterSuggestionMethodName(classModel, method, index);
        return buildSuggestionHelperInternal(classModel, method, p,
                processor.getInstanceVarExpression(classModel, rootModel), helperName, rootModel);
    }

    /**
     * Maps a suggest method signature to the arguments the generated call passes:
     * {@code m()}, {@code m(sender|current)}, {@code m(sender|current, current|context)},
     * {@code m(sender, current, context)}.
     */
    private List<CodeBlock> providerCallArgs(MethodModel suggestMethod, String senderCastVar, CodeBlock current) {
        int argCount = suggestMethod.getParameters().size();
        List<CodeBlock> args = new ArrayList<>();
        if (argCount == 1 || argCount == 2) {
            boolean firstIsArray = TypeNames.isStringArray(suggestMethod.getParameters().get(0).getType());
            args.add(firstIsArray ? CodeBlock.of("new String[]{$L}", current) : CodeBlock.of("$L", senderCastVar));
            if (argCount == 2) {
                args.add(firstIsArray ? CodeBlock.of("args") : CodeBlock.of("new String[]{$L}", current));
            }
        } else if (argCount >= 3) {
            args.add(CodeBlock.of("$L", senderCastVar));
            args.add(CodeBlock.of("new String[]{$L}", current));
            args.add(CodeBlock.of("args"));
        }
        return args;
    }

    private MethodSpec buildSuggestionHelperInternal(CommandModel classModel, MethodModel method, ParameterModel p, String instanceExpr, String helperName, CommandModel rootModel) {
        String provider = p.getSuggestProvider();
        TypeName pTypeName = TypeName.get(p.getType());
        MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(helperName)
                .addModifiers(Modifier.PRIVATE)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addParameter(processor.getSenderTypeName(), "sender")
                .addParameter(String[].class, "args");

        String senderCastVar = "sender";
        boolean needsSenderCast = false;
        MethodModel suggestMethod = p.getSuggestMethod();
        if (suggestMethod != null && !suggestMethod.getParameters().isEmpty()) {
            ParameterModel firstParam = suggestMethod.getParameters().get(0);
            TypeMirror firstParamType = firstParam.getType();
            TypeName firstParamTypeName = TypeName.get(firstParamType);

            if (!TypeNames.isStringArray(firstParamType) && !firstParamTypeName.equals(processor.getSenderTypeName())) {
                String resolveName = firstParam.getResolveName();
                if (resolveName != null) {
                    if (!resolveName.isEmpty()) {
                        MethodModel resolver = firstParam.getResolverMethod();
                        if (resolver != null) {
                            String resolverInstanceExpr = processor.getInstanceVarExpression(classModel, rootModel);
                            CodeBlock resolveExpr = BaseCommandGenerator.call(resolverInstanceExpr,
                                    resolver.getElement().getSimpleName().toString(), CodeBlock.of("sender"));
                            TypeName resolverReturnType = TypeName.get(resolver.getElement().getReturnType());
                            if (resolverReturnType.equals(firstParamTypeName)) {
                                methodSpec.addStatement("$T senderCast = $L", firstParamTypeName, resolveExpr);
                            } else {
                                methodSpec.addStatement("$T senderCast = ($T) $L", firstParamTypeName, firstParamTypeName, resolveExpr);
                            }
                            senderCastVar = "senderCast";
                        }
                    } else {
                        methodSpec.addStatement("$T senderCast = manager.resolveSender($T.class, sender)", firstParamTypeName, firstParamTypeName);
                        senderCastVar = "senderCast";
                    }
                } else if (processor.isSenderType(firstParamTypeName)) {
                    String castMethodName = "as" + Naming.simpleName(firstParamTypeName);
                    methodSpec.beginControlFlow("try");
                    methodSpec.addStatement("$T senderCast = $L($L)", firstParamTypeName, castMethodName, CodeBlock.of("sender"));
                    senderCastVar = "senderCast";
                    needsSenderCast = true;
                }
            }
        }

        CodeBlock current = CodeBlock.of("args[args.length - 1]");
        if (provider != null) {
            if (suggestMethod != null) {
                List<CodeBlock> callArgs = providerCallArgs(suggestMethod, senderCastVar, current);
                methodSpec.addStatement("return $T.filterSuggestions($L, $L)", COMMAND_MANAGER,
                        BaseCommandGenerator.call(instanceExpr, provider, callArgs.toArray(new CodeBlock[0])), current);
            } else if (p.isSuggestField()) {
                methodSpec.addStatement("return $T.filterSuggestions($L.$L, $L)",
                        COMMAND_MANAGER, instanceExpr, provider, current);
            } else {
                // The parser already reported the unknown provider; keep the generated method valid.
                methodSpec.addStatement("return $T.emptyList()", Collections.class);
            }
        } else {
            if (TypeSupport.isBooleanType(pTypeName)) {
                methodSpec.addStatement("return $T.filterSuggestions($T.asList($S, $S), $L)",
                        COMMAND_MANAGER, Arrays.class, "true", "false", current);
            } else if (processor.typeSupport().hasPlatformSuggestions(pTypeName)) {
                processor.typeSupport().emitPlatformSuggestions(methodSpec, pTypeName, current);
            } else if (processor.typeSupport().isBuiltIn(pTypeName)) {
                methodSpec.addStatement("return $T.emptyList()", Collections.class);
            } else if (processor.getSenderTypeName().equals(ClassName.OBJECT)) {
                methodSpec.addStatement("return manager.getResolver($T.class).suggest(sender, new String[]{$L}, args)",
                        pTypeName.isPrimitive() ? pTypeName.box() : pTypeName, current);
            } else {
                methodSpec.addStatement("return manager.getResolver($T.class).suggest(($T) sender, new String[]{$L}, args)",
                        pTypeName.isPrimitive() ? pTypeName.box() : pTypeName, processor.getSenderTypeName(), current);
            }
        }

        if (needsSenderCast) {
            methodSpec.nextControlFlow("catch ($T e)", CommandException.class)
                    .addStatement("return $T.emptyList()", Collections.class)
                    .endControlFlow();
        }

        return methodSpec.build();
    }

    void buildSenderCastHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        for (TypeName type : processor.getSenderTypesToCast(model)) {
            if (type.equals(processor.getSenderTypeName())) continue;
            String methodName = "as" + Naming.simpleName(type);
            typeSpec.addMethod(MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PRIVATE)
                    .returns(type)
                    .addParameter(processor.getSenderTypeName(), "sender")
                    .beginControlFlow("if (!($L instanceof $T))", processor.getSenderExpression("sender"), type)
                    .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                            CommandException.class, "invalid-sender", "Only %s can execute this command.", Naming.simpleName(type))
                    .endControlFlow()
                    .addStatement("return ($T) $L", type, processor.getSenderExpression("sender"))
                    .build());
        }
    }
}
