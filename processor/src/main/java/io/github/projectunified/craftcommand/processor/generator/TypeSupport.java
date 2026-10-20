package io.github.projectunified.craftcommand.processor.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Single source of truth for built-in and platform parameter types.
 *
 * <p>Each registered type carries all its compile-time behaviors (literal rendering, parsing codegen,
 * Brigadier argument/retrieval expressions, suggestion codegen) so a beginner reads one table
 * instead of hunting through several scattered methods.
 *
 * <p>The base instance ({@link #builtins()}) covers {@code String}, the eight primitives, and their
 * wrappers. Platform processors call {@link #register(Entry)} for platform types
 * (e.g. {@code org.bukkit.entity.Player}).
 */
public final class TypeSupport {
    private final Map<String, Entry> entries = new HashMap<>();

    private static final Set<String> NUMERIC_TYPES = Set.of(
            "int", "long", "double", "float", "short", "byte",
            "java.lang.Integer", "java.lang.Long", "java.lang.Double",
            "java.lang.Float", "java.lang.Short", "java.lang.Byte", "java.lang.Number");

    private static final Set<String> INTEGER_TYPES = Set.of(
            "int", "long", "short", "byte",
            "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte");

    /**
     * Returns true if the given type name is a numeric primitive or wrapper type.
     *
     * @param typeName the qualified type name
     * @return whether the type is numeric
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted") // public predicate; callers pick the branch that reads best
    public static boolean isNumericType(String typeName) {
        return NUMERIC_TYPES.contains(typeName);
    }

    /**
     * Returns true if the given type name is an integer numeric primitive or wrapper type.
     *
     * @param typeName the qualified type name
     * @return whether the type is an integer
     */
    public static boolean isIntegerType(String typeName) {
        return INTEGER_TYPES.contains(typeName);
    }

    /**
     * @return the shared, immutable registry of JDK built-in types.
     */
    public static TypeSupport builtins() {
        TypeSupport ts = new TypeSupport();
        ts.registerJdkTypes();
        return ts;
    }

    /**
     * Convenience to start building an entry keyed by a TypeName.
     */
    private static Entry.Builder entry(TypeName type) {
        return Entry.builder(type, 1);
    }

    private static String defaultTo(String d, String fallback) {
        return (d == null || d.isEmpty()) ? fallback : d;
    }

    /**
     * Registers a platform-specific type entry (e.g. Player, World, Location).
     * If the type is already registered, the new entry is ignored (idempotent).
     *
     * @param e the type entry to register
     */
    public void register(Entry e) {
        entries.putIfAbsent(e.type().toString(), e);
    }

    /**
     * Returns the entry for a type.
     *
     * @param type the type name
     * @return the entry, or {@code null} if not registered
     */
    public Entry get(TypeName type) {
        return entries.get(type.toString());
    }

    /**
     * Returns whether the platform provides suggestions for a type.
     *
     * @param type the type name
     * @return whether platform suggestions are available
     */
    public boolean hasPlatformSuggestions(TypeName type) {
        Entry e = entries.get(type.toString());
        return e != null && e.platformSuggestions() != null;
    }

    /**
     * Returns whether the type is boolean.
     *
     * @param type the type name
     * @return true for {@code boolean} and {@code java.lang.Boolean}
     */
    public static boolean isBooleanType(TypeName type) {
        return type.equals(TypeName.BOOLEAN) || type.equals(TypeName.BOOLEAN.box());
    }

    /**
     * Returns whether the type is registered.
     *
     * @param type the type name
     * @return true if the type is built-in or registered by a platform
     */
    public boolean isBuiltIn(TypeName type) {
        return entries.containsKey(type.toString());
    }

    /**
     * Returns the argument width of a type.
     *
     * @param type the type name
     * @return the width, defaulting to 1 for unregistered types
     */
    public int getWidth(TypeName type) {
        Entry e = entries.get(type.toString());
        return e == null ? 1 : e.width();
    }

    /**
     * Returns the primitive default literal.
     *
     * @param type the type name
     * @return "0", "0.0", "false", "'\\0'", or "null"
     */
    public String primitiveDefault(TypeName type) {
        Entry e = entries.get(type.toString());
        return e == null || e.primitiveDefault() == null ? "null" : e.primitiveDefault();
    }

    /**
     * Returns the literal expression for a default value.
     *
     * @param type the type name
     * @param defaultValue the default value text
     * @return the literal expression, or {@code null} for unregistered types
     */
    public CodeBlock literal(TypeName type, String defaultValue) {
        Entry e = entries.get(type.toString());
        return e == null ? null : e.literal().apply(defaultValue);
    }

    /**
     * Returns an expression that parses an argument string.
     *
     * @param type the type name
     * @param argExpr the argument expression
     * @return the parse expression, or {@code null}
     */
    public CodeBlock parseExpr(TypeName type, String argExpr) {
        return parseExpr(type, CodeBlock.of("$L", argExpr));
    }

    /**
     * Returns an expression that parses an argument.
     *
     * @param type the type name
     * @param argExpr the argument expression
     * @return the parse expression, or {@code null}
     */
    public CodeBlock parseExpr(TypeName type, CodeBlock argExpr) {
        Entry e = entries.get(type.toString());
        if (e != null && e.parseExpr() != null) {
            return e.parseExpr().apply(argExpr);
        }
        return null;
    }

    /**
     * Emits parse statements into the generated method.
     *
     * @param spec the generated method
     * @param type the type name
     * @param var the target variable
     * @param arg the argument expression
     */
    public void emitParse(MethodSpec.Builder spec, TypeName type, String var, String arg) {
        Entry e = entries.get(type.toString());
        if (e == null) return;
        if (e.parseExpr() != null) {
            spec.addStatement("$L = $L", var, e.parseExpr().apply(CodeBlock.of("$L", arg)));
        }
    }

    private void registerJdkTypes() {
        ClassName integer = ClassName.get(Integer.class);
        ClassName longC = ClassName.get(Long.class);
        ClassName dbl = ClassName.get(Double.class);
        ClassName flt = ClassName.get(Float.class);
        ClassName shrt = ClassName.get(Short.class);
        ClassName byt = ClassName.get(Byte.class);
        ClassName chr = ClassName.get(Character.class);
        ClassName bool = ClassName.get(Boolean.class);

        register(entry(ClassName.get(String.class))
                .primitiveDefault("null")
                .literal(d -> CodeBlock.of("$S", d == null ? "" : d))
                .parseExpr(arg -> CodeBlock.of("$L", arg))
                .build());

        registerPair(TypeName.INT, integer, "0", "parseInt", d -> CodeBlock.of("$L", defaultTo(d, "0")));
        registerPair(TypeName.LONG, longC, "0", "parseLong", d -> CodeBlock.of("$LL", defaultTo(d, "0")));
        registerPair(TypeName.DOUBLE, dbl, "0.0", "parseDouble", d -> CodeBlock.of("$L", defaultTo(d, "0.0")));
        registerPair(TypeName.FLOAT, flt, "0.0", "parseFloat", d -> CodeBlock.of("$Lf", defaultTo(d, "0.0")));
        registerPair(TypeName.SHORT, shrt, "0", "parseShort", d -> CodeBlock.of("(short) $L", defaultTo(d, "0")));
        registerPair(TypeName.BYTE, byt, "0", "parseByte", d -> CodeBlock.of("(byte) $L", defaultTo(d, "0")));

        Entry charEntry = entry(chr)
                .primitiveDefault("'\\0'")
                .literal(d -> {
                    char c = (d == null || d.isEmpty()) ? ' ' : d.charAt(0);
                    return CodeBlock.of("'$L'", c);
                })
                .parseExpr(arg -> CodeBlock.of("$L.charAt(0)", arg))
                .build();
        register(charEntry);
        alias(TypeName.CHAR, charEntry);

        Entry booleanEntry = entry(bool)
                .primitiveDefault("false")
                .literal(d -> CodeBlock.of("$L", defaultTo(d, "false")))
                .parseExpr(arg -> CodeBlock.of("$T.parseBoolean($L)", Boolean.class, arg))
                .build();
        register(booleanEntry);
        alias(TypeName.BOOLEAN, booleanEntry);
    }

    /**
     * Registers a wrapper type and aliases its primitive to the same entry.
     */
    private void registerPair(TypeName primitive, ClassName wrapper, String defaultVal, String parseMethod,
                              Function<String, CodeBlock> literal) {
        Entry entry = entry(wrapper)
                .primitiveDefault(defaultVal)
                .literal(literal)
                .parseExpr(arg -> CodeBlock.of("$T.$L($L)", wrapper, parseMethod, arg))
                .build();
        register(entry);
        alias(primitive, entry);
    }

    private void alias(TypeName type, Entry entry) {
        entries.putIfAbsent(type.toString(), entry);
    }

    /**
     * Emits platform-specific multi-argument resolution code.
     *
     * @param spec the generated method
     * @param type the type name
     * @param varName the target variable
     * @param argsVar the argument array name
     * @param index the current argument index
     * @param senderVar the sender variable name
     */
    public void emitPlatformMultiResolution(MethodSpec.Builder spec, TypeName type,
                                            String varName, String argsVar, CodeBlock index, String senderVar) {
        Entry e = entries.get(type.toString());
        if (e == null || e.platformMultiResolution() == null) {
            return;
        }
        e.platformMultiResolution().accept(spec, new Resolution(varName, argsVar, index, senderVar));
    }

    /**
     * Emits platform-specific suggestion code.
     *
     * @param spec the generated method
     * @param type the type name
     * @param currentVar the current input expression
     */
    public void emitPlatformSuggestions(MethodSpec.Builder spec, TypeName type, CodeBlock currentVar) {
        Entry e = entries.get(type.toString());
        if (e == null || e.platformSuggestions() == null) {
            return;
        }
        e.platformSuggestions().accept(spec, new Suggestion(currentVar));
    }

    /**
     * Inputs available to a platform multi-argument resolver. The generated names are provided so the platform only
     * writes the statement body.
     *
     * @param varName the local receiving the resolved value
     * @param argsVar the argument array name
     * @param index the current argument index expression
     * @param senderVar the sender variable name
     */
    public record Resolution(String varName, String argsVar, CodeBlock index, String senderVar) {
    }

    /**
     * Inputs available to a platform suggestion provider.
     *
     * @param currentVar the current input being completed
     */
    public record Suggestion(CodeBlock currentVar) {
    }

    /**
     * A registered type's behaviors. Immutable once built.
     *
     * @param type the registered type
     * @param width the number of arguments the type consumes
     * @param primitiveDefault the default expression for primitive types
     * @param literal the literal expression factory
     * @param parseExpr the parse expression factory
     * @param platformMultiResolution the multi-argument resolver emitter
     * @param platformSuggestions the suggestion emitter
     */
    public record Entry(TypeName type, int width, String primitiveDefault,
                        Function<String, CodeBlock> literal,
                        Function<CodeBlock, CodeBlock> parseExpr,
                        BiConsumer<MethodSpec.Builder, Resolution> platformMultiResolution,
                        BiConsumer<MethodSpec.Builder, Suggestion> platformSuggestions) {
        /**
         * Starts building an entry.
         *
         * @param type the type to register
         * @param width the number of arguments the type consumes
         * @return a new entry builder
         */
        public static Builder builder(TypeName type, int width) {
            return new Builder(type, width);
        }

        /**
         * Fluent builder.
         */
        public static final class Builder {
            private final TypeName type;
            private final int width;
            String primitiveDefault;
            Function<String, CodeBlock> literal;
            Function<CodeBlock, CodeBlock> parseExpr;
            BiConsumer<MethodSpec.Builder, Resolution> platformMultiResolution;
            BiConsumer<MethodSpec.Builder, Suggestion> platformSuggestions;

            Builder(TypeName type, int width) {
                this.type = type;
                this.width = width;
            }

                    /**
         * Sets the primitive default expression.
         *
         * @param v the default expression
         * @return this builder
         */
            public Builder primitiveDefault(String v) {
                this.primitiveDefault = v;
                return this;
            }

                    /**
         * Sets the literal expression factory.
         *
         * @param f the literal factory
         * @return this builder
         */
            public Builder literal(Function<String, CodeBlock> f) {
                this.literal = f;
                return this;
            }

                    /**
         * Sets the parse expression factory.
         *
         * @param f the parse factory
         * @return this builder
         */
            public Builder parseExpr(Function<CodeBlock, CodeBlock> f) {
                this.parseExpr = f;
                return this;
            }

                    /**
         * Sets the multi-argument resolution emitter.
         *
         * @param f the resolution emitter
         * @return this builder
         */
            public Builder platformMultiResolution(BiConsumer<MethodSpec.Builder, Resolution> f) {
                this.platformMultiResolution = f;
                return this;
            }

                    /**
         * Sets the suggestion emitter.
         *
         * @param f the suggestion emitter
         * @return this builder
         */
            public Builder platformSuggestions(BiConsumer<MethodSpec.Builder, Suggestion> f) {
                this.platformSuggestions = f;
                return this;
            }

            public Entry build() {
                return new Entry(type, width, primitiveDefault, literal, parseExpr,
                        platformMultiResolution, platformSuggestions);
            }
        }
    }
}
