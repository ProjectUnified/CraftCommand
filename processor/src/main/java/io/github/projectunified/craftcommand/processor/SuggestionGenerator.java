package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;
import io.github.projectunified.craftcommand.processor.parser.CommandParser;

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
    private final BaseCommandProcessor processor;

    SuggestionGenerator(BaseCommandProcessor processor) {
        this.processor = processor;
    }

    public boolean isParamDirectSuggestionAvailable(ParameterModel p) {
        if (p.getSuggestProvider() != null) return true;
        TypeName typeName = TypeName.get(p.getType());
        if (typeName.toString().equals("boolean") || typeName.toString().equals("java.lang.Boolean")) return true;
        if (processor.isPlatformBuiltInType(typeName)) return true;

        String resolveName = p.getResolveName();
        if (resolveName != null && !resolveName.isEmpty()) {
            return false; // Resolver params generate their own suggestions
        }

        return !processor.typeSupport().isBuiltIn(typeName);
    }

    public boolean isResolverParamSuggestionAvailable(ParameterModel rp) {
        if (rp.getSuggestProvider() != null) return true;
        TypeName typeName = TypeName.get(rp.getType());
        if (typeName.toString().equals("boolean") || typeName.toString().equals("java.lang.Boolean")) return true;
        if (processor.isPlatformBuiltInType(typeName)) return true;
        return !processor.typeSupport().isBuiltIn(typeName);
    }

    public boolean isParamSuggestionAvailable(ParameterModel p, CommandModel classModel) {
        if (p.getSuggestProvider() != null) return true;
        TypeName typeName = TypeName.get(p.getType());
        if (typeName.toString().equals("boolean") || typeName.toString().equals("java.lang.Boolean")) return true;
        if (processor.isPlatformBuiltInType(typeName)) return true;

        MethodModel resolverModel = p.getResolverMethod();
        if (resolverModel != null) {
            for (ParameterModel rp : resolverModel.getParameters()) {
                if (processor.isSenderParam(TypeName.get(rp.getType()), null)) continue;
                if (isParamSuggestionAvailable(rp, classModel)) return true;
            }
            return false;
        }
        ExecutableElement localRes = processor.resolveLocalResolver(p, classModel);
        if (localRes != null) {
            for (ParameterModel rp : CommandParser.parseResolverMethod(localRes).getParameters()) {
                if (processor.isSenderParam(TypeName.get(rp.getType()), null)) continue;
                if (isResolverParamSuggestionAvailable(rp)) return true;
            }
            return false;
        }

        return !processor.typeSupport().isBuiltIn(typeName);
    }

    public boolean hasAnySuggestions(MethodModel method, CommandModel classModel) {
        for (ParameterModel p : method.getParameters()) {
            if (p == method.getSenderParameter()) continue;
            if (isParamSuggestionAvailable(p, classModel)) return true;
        }
        return false;
    }

    public void buildSuggestionRouting(MethodSpec.Builder methodSpec, CommandModel model, String argsVar, String instanceVar, CommandModel rootModel) {
        boolean hasChildren = !model.getSubcommands().isEmpty() || !model.getNestedSubcommands().isEmpty();

        if (hasChildren) {
            List<String> names = processor.subcommandNames(model);
            String helperCall = null;
            MethodModel defaultMethod = model.getDefaultMethod();
            if (defaultMethod != null && !defaultMethod.getParameters().isEmpty()
                    && isParamSuggestionAvailable(defaultMethod.getParameters().get(0), model)) {
                helperCall = getParameterSuggestionMethodName(model, defaultMethod, 0);
            }

            methodSpec.beginControlFlow("if ($L.length == 1)", argsVar);
            methodSpec.addStatement("String current = $L[0]", argsVar);
            if (helperCall == null) {
                methodSpec.addStatement("return $T.filterSuggestions($L, current)",
                        ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), processor.stringList(names));
            } else if (names.isEmpty()) {
                methodSpec.addStatement("return $T.filterSuggestions($L(sender, $L), current)",
                        ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), helperCall, argsVar);
            } else {
                methodSpec.addStatement("$T<String> suggestions = new $T<>($L)", List.class, ArrayList.class, processor.stringList(names));
                methodSpec.addStatement("suggestions.addAll($L(sender, $L))", helperCall, argsVar);
                methodSpec.addStatement("return $T.filterSuggestions(suggestions, current)",
                        ClassName.get("io.github.projectunified.craftcommand", "CommandManager"));
            }
            methodSpec.endControlFlow();

            // Routing for args.length > 1
            boolean hasSubSuggestions = !model.getNestedSubcommands().isEmpty() ||
                    model.getSubcommands().stream().anyMatch(sub -> hasAnySuggestions(sub, model));

            if (hasSubSuggestions) {
                methodSpec.beginControlFlow("if ($L.length > 1)", argsVar);
                methodSpec.addStatement("String sub = $L[0].toLowerCase()", argsVar);
                methodSpec.beginControlFlow("switch (sub)");

                // Route to nested subcommand classes
                for (CommandModel child : model.getNestedSubcommands()) {
                    processor.beginCaseBlock(methodSpec, processor.collectLoweredNames(child));
                    String childHelperName = Naming.suggestHelper(child.getClassName());
                    methodSpec.addStatement("$T subArgs = $T.copyOfRange($L, 1, $L.length)", String[].class, Arrays.class, argsVar, argsVar);
                    methodSpec.addStatement("return $L(sender, subArgs)", childHelperName);
                    methodSpec.endControlFlow();
                }

                // Route to subcommand methods ONLY IF THEY HAVE SUGGESTIONS
                for (MethodModel sub : model.getSubcommands()) {
                    if (!hasAnySuggestions(sub, model)) {
                        continue;
                    }
                    processor.beginCaseBlock(methodSpec, processor.collectLoweredNames(sub));
                    methodSpec.addStatement("$T subArgs = $T.copyOfRange($L, 1, $L.length)", String[].class, Arrays.class, argsVar, argsVar);
                    buildSubcommandSuggestionRouting(methodSpec, model, sub, "subArgs");
                    methodSpec.endControlFlow();
                }

                methodSpec.endControlFlow(); // switch
                methodSpec.endControlFlow(); // if
            }
        }

        // Default command tab complete
        if (model.getDefaultMethod() != null && hasAnySuggestions(model.getDefaultMethod(), model)) {
            buildSubcommandSuggestionRouting(methodSpec, model, model.getDefaultMethod(), argsVar);
        } else {
            methodSpec.addStatement("return $T.emptyList()", Collections.class);
        }
    }

    protected void buildSubcommandSuggestionRouting(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, String argsVar) {
        List<SuggestionSlot> slots = suggestionSlots(classModel, method);
        if (slots.isEmpty()) {
            methodSpec.addStatement("return $T.emptyList()", Collections.class);
            return;
        }

        methodSpec.addStatement("if ($L.length == 0) return $T.emptyList()", argsVar, Collections.class);
        methodSpec.addStatement("int index = $L.length - 1", argsVar);

        if (slots.stream().allMatch(SuggestionSlot::hasStaticWidth)) {
            // Every width is known, so the checked bound is a constant and no accumulator is needed.
            int bound = 0;
            for (SuggestionSlot slot : slots) {
                bound += slot.staticWidth;
                if (slot.helper != null) {
                    methodSpec.beginControlFlow("if (index < $L)", bound)
                            .addStatement("return $L(sender, $L)", slot.helper, argsVar)
                            .endControlFlow();
                }
            }
        } else if (slots.size() == 1) {
            SuggestionSlot slot = slots.get(0);
            if (slot.helper != null) {
                methodSpec.beginControlFlow("if (index < $L)", slot.width)
                        .addStatement("return $L(sender, $L)", slot.helper, argsVar)
                        .endControlFlow();
            }
        } else {
            methodSpec.addStatement("int tempIdx = 0");
            for (int i = 0; i < slots.size(); i++) {
                SuggestionSlot slot = slots.get(i);
                if (slot.helper != null) {
                    methodSpec.beginControlFlow("if (index < tempIdx + $L)", slot.width)
                            .addStatement("return $L(sender, $L)", slot.helper, argsVar)
                            .endControlFlow();
                }
                if (i < slots.size() - 1) {
                    methodSpec.addStatement("tempIdx += $L", slot.width);
                }
            }
        }
        methodSpec.addStatement("return $T.emptyList()", Collections.class);
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

        for (int i = 0; i < resolverModel.getParameters().size(); i++) {
            ParameterModel rp = resolverModel.getParameters().get(i);
            if (isResolverParamSuggestionAvailable(rp)) {
                String helperName = getResolverParamSuggestionMethodName(classModel, method, p.getResolveName(), i);
                typeSpec.addMethod(buildResolverParamSuggestionHelper(classModel, method, rp, helperName, rootModel));
            }
        }
    }

    String getParameterSuggestionMethodName(CommandModel classModel, MethodModel method, int index) {
        String methodOrDefault = method.isDefault() ? "default" : method.getSubcommandName();
        return Naming.suggestMethod(classModel.getClassName(), methodOrDefault, index);
    }

    String getResolverParamSuggestionMethodName(CommandModel classModel, MethodModel method, String resolverName, int index) {
        String methodOrDefault = method.isDefault() ? "default" : method.getSubcommandName();
        return Naming.suggestMethod(classModel.getClassName(), methodOrDefault + " " + resolverName, index);
    }

    protected MethodSpec buildResolverParamSuggestionHelper(CommandModel classModel, MethodModel method, ParameterModel rp, String helperName, CommandModel rootModel) {
        return buildSuggestionHelperInternal(classModel, method, rp,
                processor.getInstanceVarExpression(classModel, rootModel), 0, helperName, "resolver param", rootModel);
    }

    protected MethodSpec buildParameterSuggestionHelper(CommandModel classModel, MethodModel method, ParameterModel p, int index, CommandModel rootModel) {
        int tempIdx = 0;
        for (int j = 0; j < index; j++) {
            ParameterModel prev = method.getParameters().get(j);
            tempIdx += processor.getBuiltInWidth(TypeName.get(prev.getType()));
        }
        String helperName = getParameterSuggestionMethodName(classModel, method, index);
        return buildSuggestionHelperInternal(classModel, method, p,
                processor.getInstanceVarExpression(classModel, rootModel), tempIdx, helperName, "parameter", rootModel);
    }

    private MethodSpec buildSuggestionHelperInternal(CommandModel classModel, MethodModel method, ParameterModel p, String instanceExpr, int tempIdxOffset, String helperName, String paramLabel, CommandModel rootModel) {
        String provider = p.getSuggestProvider();
        TypeName pTypeName = TypeName.get(p.getType());
        MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(helperName)
                .addJavadoc("Gets suggestions for " + paramLabel + " {@code $L} of method {@code $L}.\n\n"
                        + "@param sender the command sender\n"
                        + "@param args the command arguments\n"
                        + "@return the list of suggestions\n", p.getName(), method.getMethodName())
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

            if (!processor.isStringArray(firstParamType) && !firstParamTypeName.toString().equals(processor.getSenderTypeName().toString())) {
                String resolveName = firstParam.getResolveName();
                if (resolveName != null) {
                    if (!resolveName.isEmpty()) {
                        ExecutableElement resolver = ResolverLookup.findMethod(classModel.getElement(), resolveName);
                        if (resolver != null) {
                            String resolverInstanceExpr = processor.getInstanceVarExpression(classModel, rootModel);
                            String resolveExpr = String.format("%s.%s(%s)", resolverInstanceExpr, resolver.getSimpleName().toString(), "sender");
                            methodSpec.addStatement("$T senderCast = ($T) $L", firstParamTypeName, firstParamTypeName, resolveExpr);
                            senderCastVar = "senderCast";
                        }
                    } else {
                        methodSpec.addStatement("$T senderCast = ($T) manager.resolveSender($T.class, sender)", firstParamTypeName, firstParamTypeName, firstParamTypeName);
                        senderCastVar = "senderCast";
                    }
                } else if (processor.isSenderType(firstParamTypeName)) {
                    String castMethodName = "as" + BaseCommandProcessor.getSimpleName(firstParamTypeName);
                    methodSpec.beginControlFlow("try");
                    methodSpec.addStatement("$T senderCast = $L($L)", firstParamTypeName, castMethodName, CodeBlock.of("sender"));
                    senderCastVar = "senderCast";
                    needsSenderCast = true;
                }
            }
        }

        methodSpec.addStatement("int index = $L.length - 1", "args");
        methodSpec.addStatement("String currentStr = $L[index]", "args");

        if (provider != null) {
            if (suggestMethod != null) {
                int argCount = suggestMethod.getParameters().size();
                if (argCount == 0) {
                    methodSpec.addStatement("return $T.filterSuggestions($L.$L(), $L)",
                            ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider, "currentStr");
                } else if (argCount == 1) {
                    TypeMirror firstParamType = suggestMethod.getParameters().get(0).getType();
                    if (!processor.isStringArray(firstParamType)) {
                        methodSpec.addStatement("return $T.filterSuggestions($L.$L($L), $L)",
                                ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider, senderCastVar, "currentStr");
                    } else {
                        methodSpec.addStatement("return $T.filterSuggestions($L.$L(new String[]{currentStr}), currentStr)",
                                ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider);
                    }
                } else if (argCount == 2) {
                    TypeMirror firstParamType = suggestMethod.getParameters().get(0).getType();
                    if (!processor.isStringArray(firstParamType)) {
                        methodSpec.addStatement("return $T.filterSuggestions($L.$L($L, new String[]{currentStr}), currentStr)",
                                ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider, senderCastVar);
                    } else {
                        methodSpec.addStatement("return $T.filterSuggestions($L.$L(new String[]{currentStr}, $L), currentStr)",
                                ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider, "args");
                    }
                } else if (argCount == 3) {
                    methodSpec.addStatement("return $T.filterSuggestions($L.$L($L, new String[]{currentStr}, $L), currentStr)",
                            ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider, senderCastVar, "args");
                }
            } else if (processor.isField(classModel.getElement(), provider)) {
                methodSpec.addStatement("return $T.filterSuggestions($L.$L, currentStr)",
                        ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider);
            } else {
                processor.reportUnknownSuggestProvider(provider, classModel);
                methodSpec.addStatement("return $T.emptyList()", Collections.class);
            }
        } else {
            if (pTypeName.toString().equals("boolean") || pTypeName.toString().equals("java.lang.Boolean")) {
                methodSpec.addStatement("return suggestBoolean(currentStr)");
            } else if (processor.isPlatformBuiltInType(pTypeName)) {
                processor.typeSupport().emitPlatformSuggestions(methodSpec, pTypeName, senderCastVar, "args", "currentStr", String.valueOf(tempIdxOffset));
            } else if (processor.typeSupport().isBuiltIn(pTypeName)) {
                methodSpec.addStatement("return $T.emptyList()", Collections.class);
            } else if (processor.getSenderTypeName().toString().equals("java.lang.Object")) {
                methodSpec.addStatement("return manager.getResolver($T.class).suggest(sender, new String[]{currentStr}, args)",
                        pTypeName.isPrimitive() ? pTypeName.box() : pTypeName);
            } else {
                methodSpec.addStatement("return manager.getResolver($T.class).suggest(($T) sender, new String[]{currentStr}, args)",
                        pTypeName.isPrimitive() ? pTypeName.box() : pTypeName, processor.getSenderTypeName());
            }
        }

        if (needsSenderCast) {
            methodSpec.nextControlFlow("catch ($T e)", CommandException.class)
                    .addStatement("return $T.emptyList()", Collections.class)
                    .endControlFlow();
        }

        return methodSpec.build();
    }

    void buildBooleanSuggestionHelper(TypeSpec.Builder typeSpec, CommandModel model) {
        if (!processor.hasBooleanParameter(model)) return;

        typeSpec.addMethod(MethodSpec.methodBuilder("suggestBoolean")
                .addJavadoc("Suggests boolean values matching the current input (case-insensitive).\n\n"
                        + "@param current the current user input\n"
                        + "@return a list of matching boolean suggestions\n")
                .addModifiers(Modifier.PRIVATE)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addParameter(String.class, "current")
                .addStatement("if (current == null || current.isEmpty()) return $T.asList(\"true\", \"false\")", Arrays.class)
                .addStatement("$T list = new $T<>()", ParameterizedTypeName.get(List.class, String.class), ArrayList.class)
                .addStatement("String lower = current.toLowerCase()")
                .addStatement("if (\"true\".startsWith(lower)) list.add(\"true\")")
                .addStatement("if (\"false\".startsWith(lower)) list.add(\"false\")")
                .addStatement("return list")
                .build());
    }

    void buildSenderCastHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        for (TypeName type : processor.getSenderTypesToCast(model)) {
            if (type.toString().equals(processor.getSenderTypeName().toString())) continue;
            String methodName = "as" + BaseCommandProcessor.getSimpleName(type);
            typeSpec.addMethod(MethodSpec.methodBuilder(methodName)
                    .addJavadoc("Casts the command sender to {@link $T} after verification.\n\n"
                            + "@param sender the raw command sender\n"
                            + "@return the casted sender\n"
                            + "@throws CommandException if the sender is not of the expected type\n", type)
                    .addModifiers(Modifier.PRIVATE)
                    .returns(type)
                    .addParameter(processor.getSenderTypeName(), "sender")
                    .beginControlFlow("if (!($L instanceof $T))", processor.getSenderExpression("sender"), type)
                    .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                            CommandException.class, "invalid-sender", "Only %s can execute this command.", BaseCommandProcessor.getSimpleName(type))
                    .endControlFlow()
                    .addStatement("return ($T) $L", type, processor.getSenderExpression("sender"))
                    .build());
        }
    }
}
