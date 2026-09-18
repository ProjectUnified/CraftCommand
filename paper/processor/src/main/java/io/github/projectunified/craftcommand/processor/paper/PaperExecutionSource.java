package io.github.projectunified.craftcommand.processor.paper;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import io.github.projectunified.craftcommand.processor.BaseCommandProcessor;
import io.github.projectunified.craftcommand.processor.ResolverLookup;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;
import io.github.projectunified.craftcommand.processor.parser.CommandParser;

import javax.lang.model.element.ExecutableElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Brigadier-based execution generator for Paper commands.
 */
public class PaperExecutionSource {
    private final PaperCommandProcessor processor;
    private final List<PaperCommandProcessor.NodeInfo> nodes;
    private final int parsedNodeCount;

    public PaperExecutionSource(PaperCommandProcessor processor, List<PaperCommandProcessor.NodeInfo> nodes, int parsedNodeCount) {
        this.processor = processor;
        this.nodes = nodes;
        this.parsedNodeCount = parsedNodeCount;
    }

    public void generateExecution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, String instanceExpr, CommandModel rootModel) {
        // 1. Resolve and Cast Sender
        ParameterModel senderParam = method.getSenderParameter();
        TypeName senderParamTypeName = TypeName.get(senderParam.getType());
        String senderVarName = senderParamTypeName.toString().equals(processor.getSenderTypeName().toString()) ? "sender" : "senderCast";

        resolveSender(methodSpec, rootModel, senderVarName, senderParam, senderParamTypeName);
        processor.runParameterAnnotationHandlers(senderParam, senderVarName, instanceExpr, "sender", methodSpec);

        // 2. Resolve Parameters
        List<String> paramNames = new ArrayList<>();
        paramNames.add(senderVarName);

        for (int i = 0; i < method.getParameters().size(); i++) {
            ParameterModel p = method.getParameters().get(i);
            String varName = "param_" + i;
            paramNames.add(varName);

            resolveParameter(methodSpec, classModel, method, rootModel, p, varName, senderVarName);
            processor.runParameterAnnotationHandlers(p, varName, instanceExpr, senderVarName, methodSpec);
        }

        // 3. Call Target Method
        CodeBlock.Builder callBuilder = CodeBlock.builder().add("$L.$L(", instanceExpr, method.getMethodName());
        for (int i = 0; i < paramNames.size(); i++) {
            if (i > 0) callBuilder.add(", ");
            callBuilder.add("$L", paramNames.get(i));
        }
        callBuilder.add(")");
        methodSpec.addStatement(callBuilder.build());
    }

    private void resolveSender(MethodSpec.Builder methodSpec, CommandModel rootModel, String senderVarName, ParameterModel senderParam, TypeName senderParamTypeName) {
        String resolveName = senderParam.getResolveName();

        if (resolveName != null && !resolveName.isEmpty()) {
            MethodModel senderResolver = senderParam.getResolverMethod();
            if (senderResolver != null) {
                String resolverInstanceExpr = processor.getResolverInstanceExpr(senderResolver, rootModel);
                if (resolverInstanceExpr == null) {
                    methodSpec.addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                            senderParamTypeName, senderVarName, senderParamTypeName, senderParamTypeName);
                } else {
                    String resolverMethodName = senderResolver.getElement().getSimpleName().toString();
                    String resolveExpr;
                    int resolverParamCount = senderResolver.getParameters().size();
                    if (resolverParamCount == 0) {
                        resolveExpr = String.format("%s.%s()", resolverInstanceExpr, resolverMethodName);
                    } else if (resolverParamCount == 1) {
                        resolveExpr = String.format("%s.%s(%s)", resolverInstanceExpr, resolverMethodName, "sender");
                    } else {
                        resolveExpr = String.format("%s.%s(%s, %s)", resolverInstanceExpr, resolverMethodName, "sender", "new String[0]");
                    }
                    TypeName resolverReturnType = TypeName.get(senderResolver.getElement().getReturnType());
                    if (resolverReturnType.equals(senderParamTypeName)) {
                        methodSpec.addStatement("$T $L = $L", senderParamTypeName, senderVarName, resolveExpr);
                    } else {
                        methodSpec.addStatement("$T $L = ($T) $L", senderParamTypeName, senderVarName, senderParamTypeName, resolveExpr);
                    }
                }
            } else {
                methodSpec.addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                        senderParamTypeName, senderVarName, senderParamTypeName, senderParamTypeName);
            }
        } else if (resolveName != null) {
            methodSpec.addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                    senderParamTypeName, senderVarName, senderParamTypeName, senderParamTypeName);
        } else {
            if (processor.isSenderBaseType(senderParamTypeName)) {
                if (!senderVarName.equals("sender")) {
                    methodSpec.addStatement("$T $L = sender", processor.commandSourceStackClass, senderVarName);
                }
            } else {
                String castMethodName = "as" + BaseCommandProcessor.getSimpleName(senderParamTypeName);
                methodSpec.addStatement("$T $L = $L(sender)", senderParamTypeName, senderVarName, castMethodName);
            }
        }
    }

    private void resolveParameter(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, ParameterModel pm, String varName, String senderVarName) {
        TypeName pmTypeName = TypeName.get(pm.getType());

        // Check if param has @Resolve
        MethodModel resolverModel = pm.getResolverMethod();
        if (resolverModel != null) {
            resolveResolverParameters(methodSpec, classModel, method, rootModel, resolverModel, varName, senderVarName, pm);
            return;
        }

        List<PaperCommandProcessor.NodeInfo> parsedSegments = new ArrayList<>();
        for (int i = 0; i < parsedNodeCount; i++) {
            if (nodes.get(i).nodeName.equals(pm.getName())) {
                parsedSegments.add(nodes.get(i));
            }
        }

        MethodModel localResolver = pm.getResolverMethod();
        if (localResolver == null) {
            String resolveName = pm.getResolveName();
            if (resolveName != null && !resolveName.isEmpty()) {
                ExecutableElement resolverElement = ResolverLookup.findMethod(classModel.getElement(), resolveName);
                if (resolverElement != null) localResolver = CommandParser.parseResolverMethod(resolverElement);
            }
        }

        if (parsedSegments.isEmpty()) {
            if (localResolver != null) {
                List<String> argNames = resolveResolverParamsWithDefaults(methodSpec, localResolver, varName);
                String resolverSenderExpr = processor.getResolverSenderExpression(localResolver.getElement(), "sender", senderVarName, TypeName.get(method.getSenderType()));
                boolean includeSender = processor.isSenderParam(TypeName.get(localResolver.getParameters().get(0).getType()), method);
                processor.generateResolverInvocation(methodSpec, localResolver, rootModel, pmTypeName, varName, resolverSenderExpr, argNames, includeSender);
            } else if (processor.typeSupport().isBuiltIn(pmTypeName)) {
                if (pm.isOptional() && pm.getDefaultValue() != null) {
                    methodSpec.addStatement("$T $L = $L", pmTypeName, varName, processor.typeSupport().literal(pmTypeName, pm.getDefaultValue()));
                } else {
                    String defVal = pmTypeName.isPrimitive() ? processor.typeSupport().primitiveDefault(pmTypeName) : "null";
                    methodSpec.addStatement("$T $L = $L", pmTypeName, varName, defVal);
                }
            } else {
                String strVar = varName + "_str";
                methodSpec.addStatement("String $L = $T.getString(ctx, $S)", strVar,
                        ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.getName());
                methodSpec.addStatement("$T $L = ($T) manager.getResolver($T.class).resolve(sender, new String[]{$L}, $L)",
                        pmTypeName, varName, pmTypeName,
                        pmTypeName.isPrimitive() ? pmTypeName.box() : pmTypeName,
                        strVar, strVar);
            }
        } else {
            if (localResolver != null) {
                List<String> argNames = resolveResolverParamsFromBrigadier(methodSpec, localResolver, varName, method);
                String resolverSenderExpr = processor.getResolverSenderExpression(localResolver.getElement(), "sender", senderVarName, TypeName.get(method.getSenderType()));
                boolean includeSender = processor.isSenderParam(TypeName.get(localResolver.getParameters().get(0).getType()), method);
                processor.generateResolverInvocation(methodSpec, localResolver, rootModel, pmTypeName, varName, resolverSenderExpr, argNames, includeSender);
            } else if (pm.isGreedy() && !pmTypeName.toString().equals("java.lang.String")) {
                CodeBlock strExpr = CodeBlock.of("$T.getString(ctx, $S)", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.getName());
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName, processor.typeSupport().parseExpr(pmTypeName, strExpr.toString()));
            } else if (processor.typeSupport().isBuiltIn(pmTypeName)) {
                CodeBlock retrievalExpr = processor.getArgumentRetrievalExpression(pmTypeName, pm.getName());
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName, retrievalExpr);
            } else {
                String strVar = varName + "_str";
                methodSpec.addStatement("String $L = $T.getString(ctx, $S)", strVar,
                        ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.getName());
                methodSpec.addStatement("$T $L = ($T) manager.getResolver($T.class).resolve(sender, new String[]{$L}, $L)",
                        pmTypeName, varName, pmTypeName,
                        pmTypeName.isPrimitive() ? pmTypeName.box() : pmTypeName,
                        strVar, strVar);
            }
        }
    }

    private void resolveResolverParameters(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, MethodModel resolverModel, String varName, String senderVarName, ParameterModel parentParam) {
        ExecutableElement resolverElement = resolverModel.getElement();
        TypeName returnType = TypeName.get(resolverElement.getReturnType());

        boolean includeSender = !resolverModel.getParameters().isEmpty() && processor.isSenderParam(TypeName.get(resolverModel.getParameters().get(0).getType()), method);
        List<String> argNames = new ArrayList<>();

        for (int i = 0; i < resolverModel.getParameters().size(); i++) {
            ParameterModel rp = resolverModel.getParameters().get(i);
            if (processor.isSenderParam(TypeName.get(rp.getType()), method)) continue;
            String rpVarName = varName + "_rp_" + i;
            argNames.add(rpVarName);

            ParameterModel rpToResolve = rp;
            if (!rp.isOptional() && parentParam != null && parentParam.isOptional()) {
                rpToResolve = rp.asOptional(parentParam.getDefaultValue());
            }

            resolveParameter(methodSpec, classModel, method, rootModel, rpToResolve, rpVarName, senderVarName);
            processor.runParameterAnnotationHandlers(rp, rpVarName, processor.getInstanceVarExpression(classModel, rootModel), senderVarName, methodSpec);
        }

        String resolverSenderExpr = processor.getResolverSenderExpression(resolverElement, "sender", senderVarName, TypeName.get(method.getSenderType()));
        processor.generateResolverInvocation(methodSpec, resolverModel, rootModel, returnType, varName, resolverSenderExpr, argNames, includeSender);
    }

    private List<String> resolveResolverParamsWithDefaults(MethodSpec.Builder methodSpec, MethodModel localResolver, String varName) {
        List<ParameterModel> resolverParams = localResolver.getParameters();
        int startIndex = processor.firstParamIsSender(localResolver.getElement()) ? 1 : 0;
        List<String> argNames = new ArrayList<>();
        for (int j = startIndex; j < resolverParams.size(); j++) {
            ParameterModel rp = resolverParams.get(j);
            TypeName rpTypeName = TypeName.get(rp.getType());
            String rpVarName = varName + "_rp_" + (j - startIndex);
            argNames.add(rpVarName);
            if (processor.isSenderParam(rpTypeName, null)) {
                String castMethodName = "as" + BaseCommandProcessor.getSimpleName(rpTypeName);
                methodSpec.addStatement("$T $L = $L($L)", rpTypeName, rpVarName, castMethodName, "sender");
            } else {
                String defaultValue = rp.getDefaultValue();
                if (defaultValue != null && !defaultValue.isEmpty()) {
                    methodSpec.addStatement("$T $L = $L", rpTypeName, rpVarName, processor.typeSupport().literal(rpTypeName, defaultValue));
                } else {
                    String defVal = rpTypeName.isPrimitive() ? processor.typeSupport().primitiveDefault(rpTypeName) : "null";
                    methodSpec.addStatement("$T $L = $L", rpTypeName, rpVarName, defVal);
                }
            }
        }
        return argNames;
    }

    private List<String> resolveResolverParamsFromBrigadier(MethodSpec.Builder methodSpec, MethodModel localResolver, String varName, MethodModel method) {
        List<ParameterModel> resolverParams = localResolver.getParameters();
        int startIndex = processor.firstParamIsSender(localResolver.getElement()) ? 1 : 0;
        List<String> argNames = new ArrayList<>();
        for (int j = startIndex; j < resolverParams.size(); j++) {
            ParameterModel rp = resolverParams.get(j);
            TypeName rpTypeName = TypeName.get(rp.getType());
            if (processor.isSenderParam(rpTypeName, method)) continue;
            String rpVarName = varName + "_rp_" + (j - startIndex);
            argNames.add(rpVarName);
            methodSpec.addStatement("$T $L", rpTypeName, rpVarName);
            CodeBlock retrievalExpr = processor.getArgumentRetrievalExpression(rpTypeName, rp.getElement().getSimpleName().toString());
            methodSpec.addStatement("$L = $L", rpVarName, retrievalExpr);
        }
        return argNames;
    }
}
