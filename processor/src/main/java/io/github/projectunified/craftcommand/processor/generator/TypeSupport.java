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
     */
    public static boolean isNumericType(String typeName) {
        return NUMERIC_TYPES.contains(typeName);
    }

    /**
     * Returns true if the given type name is an integer numeric primitive or wrapper type.
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
    private static Entry.Builder entry(TypeName type, int width) {
        return Entry.builder(type, width);
    }

    private static String defaultTo(String d, String fallback) {
        return (d == null || d.isEmpty()) ? fallback : d;
    }

    /**
     * Register a platform-specific type entry (e.g. Player, World, Location).
     * If the type is already registered, the new entry is ignored (idempotent).
     */
    public void register(Entry e) {
        entries.putIfAbsent(e.type.toString(), e);
    }

    /**
     * @return the entry for the given type, or {@code null} if not registered.
     */
    public Entry get(TypeName type) {
        return entries.get(type.toString());
    }

    /**
     * @return true if the platform provides suggestions for the type.
     */
    public boolean hasPlatformSuggestions(TypeName type) {
        Entry e = entries.get(type.toString());
        return e != null && e.platformSuggestions != null;
    }

    /**
     * @return true for {@code boolean} and {@code java.lang.Boolean}.
     */
    public static boolean isBooleanType(TypeName type) {
        return type.equals(TypeName.BOOLEAN) || type.equals(TypeName.BOOLEAN.box());
    }

    /**
     * @return true if the type is registered (built-in or platform).
     */
    public boolean isBuiltIn(TypeName type) {
        return entries.containsKey(type.toString());
    }

    /**
     * @return the argument width, defaulting to 1 for unregistered types.
     */
    public int getWidth(TypeName type) {
        Entry e = entries.get(type.toString());
        return e == null ? 1 : e.width;
    }

    /**
     * @return the primitive default literal ("0", "0.0", "false", "'\\0'", or "null").
     */
    public String primitiveDefault(TypeName type) {
        Entry e = entries.get(type.toString());
        return e == null || e.primitiveDefault == null ? "null" : e.primitiveDefault;
    }

    /**
     * @return the literal CodeBlock for a default value, or {@code null} for unregistered types.
     */
    public CodeBlock literal(TypeName type, String defaultValue) {
        Entry e = entries.get(type.toString());
        return e == null ? null : e.literal.apply(defaultValue);
    }

    /**
     * @return an expression CodeBlock that parses the given argument string expression, or null.
     */
    public CodeBlock parseExpr(TypeName type, String argExpr) {
        return parseExpr(type, CodeBlock.of("$L", argExpr));
    }

    /**
     * @return an expression CodeBlock that parses the given argument expression, or null.
     */
    public CodeBlock parseExpr(TypeName type, CodeBlock argExpr) {
        Entry e = entries.get(type.toString());
        if (e != null && e.parseExpr != null) {
            return e.parseExpr.apply(argExpr);
        }
        return null;
    }

    /**
     * Emit parse statements (e.g. {@code var = Integer.parseInt(arg)}) into the given builder.
     */
    public void emitParse(MethodSpec.Builder spec, TypeName type, String var, String arg) {
        Entry e = entries.get(type.toString());
        if (e == null) return;
        if (e.parseExpr != null) {
            spec.addStatement("$L = $L", var, e.parseExpr.apply(CodeBlock.of("$L", arg)));
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

        register(entry(ClassName.get(String.class), 1)
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

        Entry charEntry = entry(chr, 1)
                .primitiveDefault("'\\0'")
                .literal(d -> {
                    char c = (d == null || d.isEmpty()) ? ' ' : d.charAt(0);
                    return CodeBlock.of("'$L'", c);
                })
                .parseExpr(arg -> CodeBlock.of("$L.charAt(0)", arg))
                .build();
        register(charEntry);
        alias(TypeName.CHAR, charEntry);

        Entry booleanEntry = entry(bool, 1)
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
        Entry entry = entry(wrapper, 1)
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
     * Emit platform-specific multi-arg resolution code, for example {@code location = getLocation(args, argIdx)}.
     */
    public void emitPlatformMultiResolution(MethodSpec.Builder spec, TypeName type,
                                            String varName, String argsVar, CodeBlock index, String senderVar) {
        Entry e = entries.get(type.toString());
        if (e == null || e.platformMultiResolution == null) {
            return;
        }
        e.platformMultiResolution.accept(spec, new Resolution(varName, argsVar, index, senderVar));
    }

    /**
     * Emit platform-specific suggestion code for a type.
     */
    public void emitPlatformSuggestions(MethodSpec.Builder spec, TypeName type, CodeBlock currentVar) {
        Entry e = entries.get(type.toString());
        if (e == null || e.platformSuggestions == null) {
            return;
        }
        e.platformSuggestions.accept(spec, new Suggestion(currentVar));
    }

    /**
     * Inputs available to a platform multi-argument resolver. The generated names are provided so the platform only
     * writes the statement body.
     */
    public record Resolution(String varName, String argsVar, CodeBlock index, String senderVar) {
    }

    /**
     * Inputs available to a platform suggestion provider.
     */
    public record Suggestion(CodeBlock currentVar) {
    }

    /**
     * A registered type's behaviors. Immutable once built.
     */
    public static final class Entry {
        public final TypeName type;
        public final int width;
        final String primitiveDefault;
        final Function<String, CodeBlock> literal;
        final Function<CodeBlock, CodeBlock> parseExpr;
        final BiConsumer<MethodSpec.Builder, Resolution> platformMultiResolution;
        final BiConsumer<MethodSpec.Builder, Suggestion> platformSuggestions;

        private Entry(Builder b) {
            this.type = b.type;
            this.width = b.width;
            this.primitiveDefault = b.primitiveDefault;
            this.literal = b.literal;
            this.parseExpr = b.parseExpr;
            this.platformMultiResolution = b.platformMultiResolution;
            this.platformSuggestions = b.platformSuggestions;
        }

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

            public Builder primitiveDefault(String v) {
                this.primitiveDefault = v;
                return this;
            }

            public Builder literal(Function<String, CodeBlock> f) {
                this.literal = f;
                return this;
            }

            public Builder parseExpr(Function<CodeBlock, CodeBlock> f) {
                this.parseExpr = f;
                return this;
            }

            public Builder platformMultiResolution(BiConsumer<MethodSpec.Builder, Resolution> f) {
                this.platformMultiResolution = f;
                return this;
            }

            public Builder platformSuggestions(BiConsumer<MethodSpec.Builder, Suggestion> f) {
                this.platformSuggestions = f;
                return this;
            }

            public Entry build() {
                return new Entry(this);
            }
        }
    }
}
