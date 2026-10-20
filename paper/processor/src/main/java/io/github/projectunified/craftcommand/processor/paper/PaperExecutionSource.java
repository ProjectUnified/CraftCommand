package io.github.projectunified.craftcommand.processor.paper;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import io.github.projectunified.craftcommand.processor.generator.BaseCommandGenerator;
import io.github.projectunified.craftcommand.processor.generator.LocalNames;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.lang.model.element.ExecutableElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Emits the execution body of one Brigadier node chain: sender resolution, parameter retrieval from the Brigadier
 * context, and the call into the command instance.
 *
 * @param processor the owning Paper generator
 * @param nodes the Brigadier nodes in argument order
 * @param parsedNodeCount the number of nodes already parsed
 */
public record PaperExecutionSource(PaperCommandGenerator processor, List<PaperCommandGenerator.NodeInfo> nodes,
                                   int parsedNodeCount) {

    /**
     * Emits the execution body for one method.
     *
     * @param methodSpec the generated method
     * @param classModel the command class being generated
     * @param method the command method to execute
     * @param instanceExpr the command instance expression
     * @param rootModel the root command model
     */
    public void generateExecution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, String instanceExpr, CommandModel rootModel) {
        LocalNames locals = new LocalNames();
        ParameterModel senderParam = method.senderParameter();
        TypeName senderParamTypeName = TypeName.get(senderParam.type());
        String senderVarName = senderParamTypeName.equals(processor.getSenderTypeName()) ? "sender" : "senderCast";

        processor.generateSenderResolution(methodSpec, rootModel, senderParam, senderParamTypeName, senderVarName);
        processor.runParameterAnnotationHandlers(senderParam, senderVarName, instanceExpr, "sender", methodSpec);

        List<String> paramNames = new ArrayList<>();
        paramNames.add(senderVarName);

        for (int i = 0; i < method.parameters().size(); i++) {
            ParameterModel p = method.parameters().get(i);
            String varName = locals.allocate(LocalNames.declaredName(p), "param_" + i);
            paramNames.add(varName);

            resolveParameter(methodSpec, classModel, method, rootModel, p, varName, senderVarName, locals);
            processor.runParameterAnnotationHandlers(p, varName, instanceExpr, senderVarName, methodSpec);
        }

        CodeBlock[] callArgs = paramNames.stream().map(name -> CodeBlock.of("$L", name)).toArray(CodeBlock[]::new);
        methodSpec.addStatement("$L", BaseCommandGenerator.call(instanceExpr, method.methodName(), callArgs));
    }

    private void resolveParameter(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel,
                                  ParameterModel pm, String varName, String senderVarName, LocalNames locals) {
        TypeName pmTypeName = TypeName.get(pm.type());

        MethodModel resolverModel = pm.resolverMethod();
        if (resolverModel != null) {
            resolveResolverParameters(methodSpec, classModel, method, rootModel, resolverModel, varName, senderVarName, pm, locals);
            return;
        }

        List<PaperCommandGenerator.NodeInfo> parsedSegments = new ArrayList<>();
        for (int i = 0; i < parsedNodeCount; i++) {
            if (nodes.get(i).nodeName().equals(pm.name())) {
                parsedSegments.add(nodes.get(i));
            }
        }

        if (!parsedSegments.isEmpty()) {
            if (pm.enumConstants() != null) {
                methodSpec.addStatement("$T $L = $T.valueOf($T.getString(ctx, $S))",
                        pmTypeName, varName, pmTypeName,
                        ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.name());
            } else if (pm.greedy() && !pmTypeName.equals(ClassName.get(String.class))) {
                CodeBlock strExpr = CodeBlock.of("$T.getString(ctx, $S)", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.name());
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName, processor.typeSupport().parseExpr(pmTypeName, strExpr));
            } else if (processor.typeSupport().isBuiltIn(pmTypeName)) {
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName,
                        processor.getArgumentRetrievalExpression(pmTypeName, pm.name()));
            } else {
                generateManagerResolution(methodSpec, pm, pmTypeName, varName);
            }
        } else if (pm.enumConstants() != null) {
            CodeBlock defaultValue = pm.optional() && pm.defaultValue() != null
                    ? CodeBlock.of("$T.valueOf($S)", pmTypeName, pm.defaultValue())
                    : CodeBlock.of("null");
            methodSpec.addStatement("$T $L = $L", pmTypeName, varName, defaultValue);
        } else if (processor.typeSupport().isBuiltIn(pmTypeName)) {
            if (pm.optional() && pm.defaultValue() != null) {
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName,
                        processor.typeSupport().literal(pmTypeName, pm.defaultValue()));
            } else {
                String defVal = pmTypeName.isPrimitive() ? processor.typeSupport().primitiveDefault(pmTypeName) : "null";
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName, defVal);
            }
        } else {
            generateManagerResolution(methodSpec, pm, pmTypeName, varName);
        }
    }

    private void generateManagerResolution(MethodSpec.Builder methodSpec, ParameterModel pm, TypeName pmTypeName, String varName) {
        String strVar = varName + "_str";
        methodSpec.addStatement("String $L = $T.getString(ctx, $S)", strVar,
                ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.name());
        methodSpec.addStatement("$T $L = manager.getResolver($T.class).resolve(sender, new String[]{$L}, $T.arguments(ctx))",
                pmTypeName, varName,
                pmTypeName.isPrimitive() ? pmTypeName.box() : pmTypeName,
                strVar, processor.paperSuggestionsClass);
    }

    private void resolveResolverParameters(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel,
                                           MethodModel resolverModel, String varName, String senderVarName, ParameterModel parentParam, LocalNames locals) {
        ExecutableElement resolverElement = resolverModel.element();
        TypeName returnType = TypeName.get(resolverElement.getReturnType());

        boolean includeSender = !resolverModel.parameters().isEmpty()
                && processor.isSenderParam(TypeName.get(resolverModel.parameters().get(0).type()), method);
        List<String> argNames = new ArrayList<>();

        for (int i = 0; i < resolverModel.parameters().size(); i++) {
            ParameterModel rp = resolverModel.parameters().get(i);
            if (processor.isSenderParam(TypeName.get(rp.type()), method)) continue;
            String rpVarName = locals.allocate(LocalNames.declaredName(rp), varName + "_rp_" + i);
            argNames.add(rpVarName);

            ParameterModel rpToResolve = rp;
            if (!rp.optional() && parentParam != null && parentParam.optional()) {
                rpToResolve = rp.asOptional(parentParam.defaultValue());
            }

            resolveParameter(methodSpec, classModel, method, rootModel, rpToResolve, rpVarName, senderVarName, locals);
            processor.runParameterAnnotationHandlers(rp, rpVarName, processor.getInstanceVarExpression(classModel, rootModel), senderVarName, methodSpec);
        }

        CodeBlock resolverSenderExpr = processor.getResolverSenderExpression(resolverElement, "sender", senderVarName, TypeName.get(method.getSenderType()));
        processor.generateResolverInvocation(methodSpec, resolverModel, rootModel, returnType, varName, resolverSenderExpr, argNames, includeSender);
    }
}
