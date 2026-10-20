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
 */
public class PaperExecutionSource {
    private final PaperCommandGenerator processor;
    private final List<PaperCommandGenerator.NodeInfo> nodes;
    private final int parsedNodeCount;

    public PaperExecutionSource(PaperCommandGenerator processor, List<PaperCommandGenerator.NodeInfo> nodes, int parsedNodeCount) {
        this.processor = processor;
        this.nodes = nodes;
        this.parsedNodeCount = parsedNodeCount;
    }

    public void generateExecution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, String instanceExpr, CommandModel rootModel) {
        LocalNames locals = new LocalNames();
        ParameterModel senderParam = method.getSenderParameter();
        TypeName senderParamTypeName = TypeName.get(senderParam.getType());
        String senderVarName = senderParamTypeName.toString().equals(processor.getSenderTypeName().toString()) ? "sender" : "senderCast";

        resolveSender(methodSpec, rootModel, senderVarName, senderParam, senderParamTypeName);
        processor.runParameterAnnotationHandlers(senderParam, senderVarName, instanceExpr, "sender", methodSpec);

        List<String> paramNames = new ArrayList<>();
        paramNames.add(senderVarName);

        for (int i = 0; i < method.getParameters().size(); i++) {
            ParameterModel p = method.getParameters().get(i);
            String varName = locals.allocate(LocalNames.declaredName(p), "param_" + i);
            paramNames.add(varName);

            resolveParameter(methodSpec, classModel, method, rootModel, p, varName, senderVarName, locals);
            processor.runParameterAnnotationHandlers(p, varName, instanceExpr, senderVarName, methodSpec);
        }

        CodeBlock[] callArgs = paramNames.stream().map(name -> CodeBlock.of("$L", name)).toArray(CodeBlock[]::new);
        methodSpec.addStatement("$L", BaseCommandGenerator.call(instanceExpr, method.getMethodName(), callArgs));
    }

    private void resolveSender(MethodSpec.Builder methodSpec, CommandModel rootModel, String senderVarName, ParameterModel senderParam, TypeName senderParamTypeName) {
        String resolveName = senderParam.getResolveName();

        if (resolveName != null && !resolveName.isEmpty()) {
            MethodModel senderResolver = senderParam.getResolverMethod();
            String resolverInstanceExpr = senderResolver != null
                    ? processor.getResolverInstanceExpr(senderResolver, rootModel)
                    : null;
            if (resolverInstanceExpr == null) {
                methodSpec.addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                        senderParamTypeName, senderVarName, senderParamTypeName, senderParamTypeName);
                return;
            }

            String resolverMethodName = senderResolver.getElement().getSimpleName().toString();
            int resolverParamCount = senderResolver.getParameters().size();
            List<CodeBlock> resolveArgs = new ArrayList<>();
            if (resolverParamCount >= 1) resolveArgs.add(CodeBlock.of("sender"));
            if (resolverParamCount >= 2) resolveArgs.add(CodeBlock.of("new String[0]"));
            CodeBlock resolveExpr = BaseCommandGenerator.call(resolverInstanceExpr, resolverMethodName,
                    resolveArgs.toArray(new CodeBlock[0]));
            TypeName resolverReturnType = TypeName.get(senderResolver.getElement().getReturnType());
            if (resolverReturnType.equals(senderParamTypeName)) {
                methodSpec.addStatement("$T $L = $L", senderParamTypeName, senderVarName, resolveExpr);
            } else {
                methodSpec.addStatement("$T $L = ($T) $L", senderParamTypeName, senderVarName, senderParamTypeName, resolveExpr);
            }
        } else if (resolveName != null) {
            methodSpec.addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                    senderParamTypeName, senderVarName, senderParamTypeName, senderParamTypeName);
        } else if (processor.isSenderBaseType(senderParamTypeName)) {
            if (!senderVarName.equals("sender")) {
                methodSpec.addStatement("$T $L = sender", processor.commandSourceStackClass, senderVarName);
            }
        } else {
            String castMethodName = "as" + io.github.projectunified.craftcommand.processor.generator.Naming.simpleName(senderParamTypeName);
            methodSpec.addStatement("$T $L = $L(sender)", senderParamTypeName, senderVarName, castMethodName);
        }
    }

    private void resolveParameter(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel,
                                  ParameterModel pm, String varName, String senderVarName, LocalNames locals) {
        TypeName pmTypeName = TypeName.get(pm.getType());

        MethodModel resolverModel = pm.getResolverMethod();
        if (resolverModel != null) {
            resolveResolverParameters(methodSpec, classModel, method, rootModel, resolverModel, varName, senderVarName, pm, locals);
            return;
        }

        List<PaperCommandGenerator.NodeInfo> parsedSegments = new ArrayList<>();
        for (int i = 0; i < parsedNodeCount; i++) {
            if (nodes.get(i).nodeName.equals(pm.getName())) {
                parsedSegments.add(nodes.get(i));
            }
        }

        if (!parsedSegments.isEmpty()) {
            if (pm.isGreedy() && !pmTypeName.toString().equals("java.lang.String")) {
                CodeBlock strExpr = CodeBlock.of("$T.getString(ctx, $S)", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.getName());
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName, processor.typeSupport().parseExpr(pmTypeName, strExpr));
            } else if (processor.typeSupport().isBuiltIn(pmTypeName)) {
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName,
                        processor.getArgumentRetrievalExpression(pmTypeName, pm.getName()));
            } else {
                generateManagerResolution(methodSpec, pm, pmTypeName, varName);
            }
        } else if (processor.typeSupport().isBuiltIn(pmTypeName)) {
            if (pm.isOptional() && pm.getDefaultValue() != null) {
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName,
                        processor.typeSupport().literal(pmTypeName, pm.getDefaultValue()));
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
                ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.getName());
        methodSpec.addStatement("$T $L = ($T) manager.getResolver($T.class).resolve(sender, new String[]{$L}, $L)",
                pmTypeName, varName, pmTypeName,
                pmTypeName.isPrimitive() ? pmTypeName.box() : pmTypeName,
                strVar, strVar);
    }

    private void resolveResolverParameters(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel,
                                           MethodModel resolverModel, String varName, String senderVarName, ParameterModel parentParam, LocalNames locals) {
        ExecutableElement resolverElement = resolverModel.getElement();
        TypeName returnType = TypeName.get(resolverElement.getReturnType());

        boolean includeSender = !resolverModel.getParameters().isEmpty()
                && processor.isSenderParam(TypeName.get(resolverModel.getParameters().get(0).getType()), method);
        List<String> argNames = new ArrayList<>();

        for (int i = 0; i < resolverModel.getParameters().size(); i++) {
            ParameterModel rp = resolverModel.getParameters().get(i);
            if (processor.isSenderParam(TypeName.get(rp.getType()), method)) continue;
            String rpVarName = locals.allocate(LocalNames.declaredName(rp), varName + "_rp_" + i);
            argNames.add(rpVarName);

            ParameterModel rpToResolve = rp;
            if (!rp.isOptional() && parentParam != null && parentParam.isOptional()) {
                rpToResolve = rp.asOptional(parentParam.getDefaultValue());
            }

            resolveParameter(methodSpec, classModel, method, rootModel, rpToResolve, rpVarName, senderVarName, locals);
            processor.runParameterAnnotationHandlers(rp, rpVarName, processor.getInstanceVarExpression(classModel, rootModel), senderVarName, methodSpec);
        }

        CodeBlock resolverSenderExpr = processor.getResolverSenderExpression(resolverElement, "sender", senderVarName, TypeName.get(method.getSenderType()));
        processor.generateResolverInvocation(methodSpec, resolverModel, rootModel, returnType, varName, resolverSenderExpr, argNames, includeSender);
    }
}
