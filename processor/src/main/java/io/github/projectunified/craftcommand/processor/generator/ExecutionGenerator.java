package io.github.projectunified.craftcommand.processor.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import java.util.Arrays;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.extension.MethodAnnotationHandler;
import io.github.projectunified.craftcommand.processor.extension.ParameterAnnotationHandler;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the execution path of a command wrapper: the routing that dispatches an argument array to the
 * matching subcommand or default method, the resolution of each parameter, and the call into the command
 * instance.
 *
 * <p>Argument access goes through {@link ArgCursor}, so the emitted code reads {@code args[1]} wherever the
 * offset is known and only falls back to a cursor variable when a parameter is consumed conditionally or by a
 * runtime resolver.
 */
final class ExecutionGenerator {
    private final BaseCommandGenerator processor;

    ExecutionGenerator(BaseCommandGenerator processor) {
        this.processor = processor;
    }


    protected void buildExecutionRouting(MethodSpec.Builder methodSpec, CommandModel model, String argsVar, String instanceVar, CommandModel rootModel, String returnStatement) {
        if (!model.getSubcommands().isEmpty() || !model.getNestedSubcommands().isEmpty()) {
            methodSpec.beginControlFlow("if ($L.length >= 1)", argsVar);
            methodSpec.addStatement("String sub = $L[0].toLowerCase()", argsVar);
            methodSpec.beginControlFlow("switch (sub)");

            // 1. Nested subcommand classes
            for (CommandModel child : model.getNestedSubcommands()) {
                BaseCommandGenerator.beginCaseBlock(methodSpec, processor.collectLoweredNames(child));
                processor.onBeforeExecute(methodSpec, child.getElement(), returnStatement);
                String helperMethodName = Naming.executeHelper(child.getClassName());
                methodSpec.addStatement("$T subArgs = $T.copyOfRange($L, 1, $L.length)", String[].class, Arrays.class, argsVar, argsVar);
                methodSpec.addStatement("$L(sender, subArgs)", helperMethodName);
                methodSpec.addStatement("$L", returnStatement);
                methodSpec.endControlFlow();
            }

            // 2. Subcommand methods
            for (MethodModel sub : model.getSubcommands()) {
                BaseCommandGenerator.beginCaseBlock(methodSpec, processor.collectLoweredNames(sub));
                processor.onBeforeExecute(methodSpec, sub.getElement(), returnStatement);
                buildMethodExecution(methodSpec, model, sub, argsVar, 1, instanceVar, rootModel);
                methodSpec.addStatement("$L", returnStatement);
                methodSpec.endControlFlow();
            }

            methodSpec.endControlFlow(); // switch
            methodSpec.endControlFlow(); // if
        }

        // 3. Default method
        if (model.getDefaultMethod() != null) {
            processor.onBeforeExecute(methodSpec, model.getDefaultMethod().getElement(), returnStatement);
            buildMethodExecution(methodSpec, model, model.getDefaultMethod(), argsVar, 0, instanceVar, rootModel);
        } else {
            processor.generateUnknownSubcommandMessage(methodSpec, model);
        }
    }


    protected void buildMethodExecution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, String argsVar, int initialOffset, String instanceVar, CommandModel rootModel) {
        // 1. Resolve and Cast Sender Parameter
        ParameterModel senderParam = method.getSenderParameter();
        TypeName senderParamTypeName = TypeName.get(senderParam.getType());
        String senderVarName = senderParamTypeName.toString().equals(processor.getSenderTypeName().toString()) ? "sender" : "senderCast";
        MethodSlot slot = new MethodSlot(methodSpec, classModel, method, rootModel, instanceVar, senderVarName);

        resolveSender(slot, senderParam, senderParamTypeName);
        runSPIHandlers(methodSpec, method, instanceVar, senderVarName, senderParam);

        // 2. Minimum Argument Count Check
        int staticRequiredCount = 0;
        boolean hasDynamic = false;
        for (ParameterModel p : method.getParameters()) {
            TypeName pTypeName = TypeName.get(p.getType());
            MethodModel resolverModel = p.getResolverMethod();
            if (resolverModel == null && !processor.typeSupport().isBuiltIn(pTypeName)) {
                hasDynamic = true;
            }
            if (!p.isOptional()) {
                if (resolverModel != null) {
                    staticRequiredCount += processor.getLocalResolverMinWidth(resolverModel, method);
                } else {
                    staticRequiredCount += processor.typeSupport().getWidth(pTypeName);
                }
            }
        }

        int totalRequired = staticRequiredCount + initialOffset;
        if (staticRequiredCount > 0) {
            methodSpec.beginControlFlow("if ($L.length < $L)", argsVar, totalRequired)
                    .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                            CommandException.class, "usage", "Usage: %s", processor.getUsage(method))
                    .endControlFlow();
        }

        // 3. Resolve Parameters
        ArgCursor args = new ArgCursor(methodSpec, argsVar, initialOffset, hasDynamic);
        List<String> paramNames = new ArrayList<>();
        paramNames.add(senderVarName);

        for (int i = 0; i < method.getParameters().size(); i++) {
            ParameterModel p = method.getParameters().get(i);
            String varName = slot.localFor(p, "param_" + i);
            paramNames.add(varName);

            resolveParameter(slot, args, p, varName, i);
            runParameterAnnotationHandlers(p, varName, instanceVar, senderVarName, methodSpec);
        }

        // 5. Invoke Target Method
        CodeBlock[] callArgs = paramNames.stream().map(name -> CodeBlock.of("$L", name)).toArray(CodeBlock[]::new);
        methodSpec.addStatement("$L", BaseCommandGenerator.call(instanceVar, method.getMethodName(), callArgs));
    }


    void resolveSender(MethodSlot slot, ParameterModel senderParam, TypeName senderParamTypeName) {
        String resolveName = senderParam.getResolveName();

        if (resolveName != null && !resolveName.isEmpty()) {
            MethodModel senderResolver = senderParam.getResolverMethod();
            if (senderResolver != null) {
                String resolverInstanceExpr = processor.getResolverInstanceExpr(senderResolver, slot.rootModel());
                if (resolverInstanceExpr == null) {
                    slot.methodSpec().addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                            senderParamTypeName, slot.senderVarName(), senderParamTypeName, senderParamTypeName);
                } else {
                    String resolverMethodName = senderResolver.getElement().getSimpleName().toString();
                    int resolverParamCount = senderResolver.getParameters().size();
                    List<CodeBlock> resolveArgs = new ArrayList<>();
                    if (resolverParamCount >= 1) resolveArgs.add(CodeBlock.of("sender"));
                    if (resolverParamCount == 2) resolveArgs.add(CodeBlock.of("sender"));
                    if (resolverParamCount >= 3) {
                        resolveArgs.add(CodeBlock.of("new String[0]"));
                        resolveArgs.add(CodeBlock.of("sender"));
                    }
                    CodeBlock resolveExpr = BaseCommandGenerator.call(resolverInstanceExpr, resolverMethodName,
                            resolveArgs.toArray(new CodeBlock[0]));
                    TypeName resolverReturnType = TypeName.get(senderResolver.getElement().getReturnType());
                    if (resolverReturnType.equals(senderParamTypeName)) {
                        slot.methodSpec().addStatement("$T $L = $L", senderParamTypeName, slot.senderVarName(), resolveExpr);
                    } else {
                        slot.methodSpec().addStatement("$T $L = ($T) $L", senderParamTypeName, slot.senderVarName(), senderParamTypeName, resolveExpr);
                    }
                }
            } else {
                slot.methodSpec().addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                        senderParamTypeName, slot.senderVarName(), senderParamTypeName, senderParamTypeName);
            }
        } else if (resolveName != null) {
            slot.methodSpec().addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                    senderParamTypeName, slot.senderVarName(), senderParamTypeName, senderParamTypeName);
        } else {
            if (!slot.senderVarName().equals("sender")) {
                if (!processor.isSenderBaseType(senderParamTypeName)) {
                    if (processor.isSenderType(senderParamTypeName)) {
                        String castMethodName = "as" + Naming.simpleName(senderParamTypeName);
                        slot.methodSpec().addStatement("$T $L = $L(sender)", senderParamTypeName, slot.senderVarName(), castMethodName);
                    } else {
                        slot.methodSpec().addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                                senderParamTypeName, slot.senderVarName(), senderParamTypeName, senderParamTypeName);
                    }
                } else {
                    slot.methodSpec().addStatement("$T $L = sender", processor.getSenderTypeName(), slot.senderVarName());
                }
            }
        }
    }


    void resolveParameter(MethodSlot slot, ArgCursor cursor, ParameterModel p, String varName, int paramIndex) {
        TypeName pTypeName = TypeName.get(p.getType());

        // 1. Resolver model from @Resolve("name")
        MethodModel resolverModel = p.getResolverMethod();
        if (resolverModel != null) {
            resolveResolverParameters(slot, cursor, resolverModel, varName, p);
            return;
        }

        // 2. Greedy array
        if (p.isGreedy() && pTypeName instanceof ArrayTypeName) {
            TypeName componentType = ((ArrayTypeName) pTypeName).componentType();
            TypeName boxedComponent = componentType.isPrimitive() ? componentType.box() : componentType;

            if (cursor.isDeclared() && !p.isOptional()) {
                slot.methodSpec().beginControlFlow("if ($L)", cursor.notEnoughArguments(1))
                        .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                                CommandException.class, "missing-argument", "Missing arguments for parameter: %s", p.getName())
                        .endControlFlow();
            }

            slot.methodSpec().addStatement("$T[] $L_raw = $T.copyOfRange($L, $L, $L.length)", String.class, varName, Arrays.class, cursor.argsVariable(), cursor.index(), cursor.argsVariable());
            slot.methodSpec().addStatement("$T[] $L = new $T[$L_raw.length]", componentType, varName, componentType, varName);
            slot.methodSpec().beginControlFlow("for (int j = 0; j < $L_raw.length; j++)", varName);
            CodeBlock parse = processor.typeSupport().parseExpr(componentType, varName + "_raw[j]");
            if (parse != null) {
                slot.methodSpec().addStatement("$L[j] = $L", varName, parse);
            } else {
                slot.methodSpec().addStatement("$L[j] = $T.valueOf($L_raw[j])", varName, boxedComponent, varName);
            }
            slot.methodSpec().endControlFlow();
            return;
        }

        // 4. Greedy String or Object
        if (p.isGreedy()) {
            if (pTypeName.toString().equals("java.lang.String")) {
                if (p.isOptional()) {
                    String defVal = p.getDefaultValue() == null ? "null" : CodeBlock.of("$S", p.getDefaultValue()).toString();
                    slot.methodSpec().addStatement("$T $L = $L >= $L.length ? $L : String.join($S, $L)",
                            pTypeName, varName, cursor.index(), cursor.argsVariable(), defVal, " ", cursor.sliceFromHere());
                } else {
                    slot.methodSpec().addStatement("$T $L = String.join($S, $L)", pTypeName, varName, " ", cursor.sliceFromHere());
                }
            } else {
                CodeBlock greedyExpr = CodeBlock.of("String.join($S, $L)", " ", cursor.sliceFromHere());
                CodeBlock parseExpr = processor.typeSupport().parseExpr(pTypeName, greedyExpr);
                if (parseExpr != null) {
                    slot.methodSpec().addStatement("$T $L = $L", pTypeName, varName, parseExpr);
                } else {
                    slot.methodSpec().addStatement("String greedy_$L = $L", paramIndex, greedyExpr);
                    slot.methodSpec().addStatement("$T $L", pTypeName, varName);
                    processor.typeSupport().emitParse(slot.methodSpec(), pTypeName, varName, "greedy_" + paramIndex);
                }
            }
            return;
        }

        // 5. Built-in or Platform Types
        if (processor.typeSupport().isBuiltIn(pTypeName)) {
            int width = processor.typeSupport().getWidth(pTypeName);
            if (cursor.isDeclared() && !p.isOptional()) {
                slot.methodSpec().beginControlFlow("if ($L)", cursor.notEnoughArguments(width))
                        .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                                CommandException.class, "missing-argument", "Missing arguments for parameter: %s", p.getName())
                        .endControlFlow();
            }

            if (width > 1) {
                // Multi-arg platform type (e.g. Location), which may be consumed conditionally when optional
                if (p.isOptional()) {
                    cursor.requireIndex();
                    slot.methodSpec().addStatement("$T $L = $L", pTypeName, varName,
                            processor.typeSupport().primitiveDefault(pTypeName));
                    slot.methodSpec().beginControlFlow("if (!($L))", cursor.notEnoughArguments(width));
                    emitPlatformMultiResolution(slot.methodSpec(), pTypeName, varName, cursor.argsVariable(), cursor, slot.senderVarName());
                    cursor.advance(width);
                    slot.methodSpec().endControlFlow();
                } else {
                    slot.methodSpec().addStatement("$T $L", pTypeName, varName);
                    emitPlatformMultiResolution(slot.methodSpec(), pTypeName, varName, cursor.argsVariable(), cursor, slot.senderVarName());
                    cursor.advance(width);
                }
            } else {
                // Single-arg type
                if (p.isOptional()) {
                    // An optional parameter consumes its argument only when present, which needs a mutable index.
                    cursor.requireIndex();
                    CodeBlock defLit = processor.typeSupport().literal(pTypeName, p.getDefaultValue());
                    if (defLit == null) defLit = CodeBlock.of("null");
                    slot.methodSpec().addStatement("$T $L = $L >= $L.length ? $L : $L",
                            pTypeName, varName, cursor.index(), cursor.argsVariable(), defLit, processor.typeSupport().parseExpr(pTypeName, cursor.take()));
                } else {
                    slot.methodSpec().addStatement("$T $L = $L", pTypeName, varName, processor.typeSupport().parseExpr(pTypeName, cursor.take()));
                }
            }
            return;
        }

        // 6. Dynamic Manager Resolver (Fallback)
        slot.methodSpec().addStatement("$T $L", pTypeName, varName);
        String defValLiteral = p.getDefaultValue() == null ? "null" : CodeBlock.of("$S", p.getDefaultValue()).toString();
        slot.methodSpec().addStatement("$L = manager.resolveParameter(sender, $T.class, $L, $L, $S, $L, $L)",
                varName, pTypeName.isPrimitive() ? pTypeName.box() : pTypeName,
                cursor.argsVariable(), cursor.runtimeCursor(), p.getName(), p.isOptional(), defValLiteral);
    }


    /**
     * Emits the resolution of a platform type that spans several arguments, then consumes them.
     */
    private void emitPlatformMultiResolution(MethodSpec.Builder methodSpec, TypeName pTypeName, String varName,
                                             String argsVar, ArgCursor cursor, String senderVarName) {
        processor.typeSupport().emitPlatformMultiResolution(methodSpec, pTypeName, varName, argsVar, cursor.index(),
                senderVarName);
    }


    void resolveResolverParameters(MethodSlot slot, ArgCursor cursor, MethodModel resolverModel, String varName, ParameterModel parentParam) {
        ExecutableElement resolverElement = resolverModel.getElement();
        TypeName returnType = TypeName.get(resolverElement.getReturnType());

        boolean includeSender = !resolverModel.getParameters().isEmpty() && processor.isSenderParam(TypeName.get(resolverModel.getParameters().get(0).getType()), slot.method());
        List<String> argNames = new ArrayList<>();

        for (int i = 0; i < resolverModel.getParameters().size(); i++) {
            ParameterModel rp = resolverModel.getParameters().get(i);
            if (processor.isSenderParam(TypeName.get(rp.getType()), slot.method())) continue;
            String rpVarName = slot.localFor(rp, varName + "_rp_" + i);
            argNames.add(rpVarName);

            ParameterModel rpToResolve = rp;
            if (!rp.isOptional() && parentParam != null && parentParam.isOptional()) {
                rpToResolve = rp.asOptional(parentParam.getDefaultValue());
            }

            resolveParameter(slot, cursor, rpToResolve, rpVarName, i);
            runParameterAnnotationHandlers(rp, rpVarName, processor.getInstanceVarExpression(slot.classModel(), slot.rootModel()), slot.senderVarName(), slot.methodSpec());
        }

        CodeBlock resolverSenderExpr = processor.getResolverSenderExpression(resolverElement, slot.method().getSenderParameter().getName(), slot.senderVarName(), TypeName.get(slot.method().getSenderType()));
        generateResolverInvocation(slot.methodSpec(), resolverModel, slot.rootModel(), returnType, varName, resolverSenderExpr, argNames, includeSender);
    }


    // ── Tab Completion & Suggestions ──

    void generateResolverInvocation(MethodSpec.Builder methodSpec, MethodModel resolverModel, CommandModel rootModel, TypeName pTypeName, String varName, CodeBlock senderExpr, List<String> resolverArgVarNames, boolean includeSender) {
        ExecutableElement localResolver = resolverModel.getElement();
        String resolverInstanceExpr = processor.getResolverInstanceExpr(resolverModel, rootModel);
        if (resolverInstanceExpr == null) {
            methodSpec.addStatement("$T $L = $L", pTypeName, varName, pTypeName.isPrimitive() ? "false" : "null");
            return;
        }
        List<CodeBlock> resolveArgs = new ArrayList<>();
        if (includeSender) resolveArgs.add(senderExpr);
        for (String argName : resolverArgVarNames) resolveArgs.add(CodeBlock.of("$L", argName));
        CodeBlock resolveCall = BaseCommandGenerator.call(resolverInstanceExpr,
                localResolver.getSimpleName().toString(), resolveArgs.toArray(new CodeBlock[0]));
        TypeName resolverReturnType = TypeName.get(localResolver.getReturnType());
        if (resolverReturnType.equals(pTypeName)) {
            methodSpec.addStatement("$T $L = $L", pTypeName, varName, resolveCall);
        } else {
            methodSpec.addStatement("$T $L = ($T) $L", pTypeName, varName, pTypeName, resolveCall);
        }
    }


    // ── SPI Invocation Helpers ──

    private void runSPIHandlers(MethodSpec.Builder methodSpec, MethodModel method, String instanceVar, String senderVarName, ParameterModel senderParam) {
        for (MethodAnnotationHandler<?> handler : processor.methodHandlers()) {
            Annotation ann = method.getElement().getAnnotation(handler.annotationType());
            if (ann != null) {
                invokeMethodHandler(handler, ann, method, instanceVar, senderVarName, methodSpec);
            }
        }
        runParameterAnnotationHandlers(senderParam, senderVarName, instanceVar, "sender", methodSpec);
    }


    void runParameterAnnotationHandlers(ParameterModel param, String varName, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec) {
        VariableElement element = param.getElement();
        for (ParameterAnnotationHandler<?> handler : processor.parameterHandlers()) {
            Annotation ann = element.getAnnotation(handler.annotationType());
            if (ann != null) {
                invokeParameterHandler(handler, ann, param.withName(element.getSimpleName().toString()), varName, instanceExpr, senderVar, methodSpec);
            }
        }
    }

    private <A extends Annotation> void invokeParameterHandler(ParameterAnnotationHandler<A> handler, Annotation annotation, ParameterModel parameter, String varName, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec) {
        handler.handle((A) annotation, parameter, varName, instanceExpr, senderVar, methodSpec);
    }

    private <A extends Annotation> void invokeMethodHandler(MethodAnnotationHandler<A> handler, Annotation annotation, MethodModel method, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec) {
        handler.handle((A) annotation, method, instanceExpr, senderVar, methodSpec);
    }
}
