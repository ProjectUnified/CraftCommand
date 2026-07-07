package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Single source of truth for built-in and platform parameter types.
 *
 * <p>Replaces the four scattered {@code if (name.equals("int") || name.equals("java.lang.Integer"))}
 * chains that previously lived in {@link BaseCommandProcessor} and the platform processors.
 * Each registered type carries all its compile-time behaviors (literal rendering, parsing codegen,
 * Brigadier argument/retrieval expressions, suggestion codegen) so a beginner reads one table
 * instead of hunting through four methods.
 *
 * <p>The base instance ({@link #builtins()}) covers {@code String}, the eight primitives, and their
 * wrappers. Platform processors call {@link #register(Entry)} for platform types
 * (e.g. {@code org.bukkit.entity.Player}).
 */
public final class TypeSupport {
    private final Map<String, Entry> entries = new HashMap<>();

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

    /**
     * Convenience to start building an entry keyed by a wrapper ClassName but storing under a primitive key.
     */
    private static Entry.Builder entry(TypeName keyType, ClassName wrapper, int width) {
        return Entry.builder(keyType, width);
    }

    private static String defaultTo(String d, String fallback) {
        return (d == null || d.isEmpty()) ? fallback : d;
    }

    /**
     * Register a platform-specific type entry (e.g. Player, World, Location).
     */
    public void register(Entry e) {
        entries.put(e.type.toString(), e);
    }

    /**
     * @return the entry for the given type, or {@code null} if not registered.
     */
    public Entry get(TypeName type) {
        return entries.get(type.toString());
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
     * Emit parse statements (e.g. {@code var = Integer.parseInt(arg)}) into the given builder.
     */
    public void emitParse(MethodSpec.Builder spec, TypeName type, String var, String arg) {
        Entry e = entries.get(type.toString());
        if (e == null || e.parse == null) {
            return;
        }
        e.parse.accept(spec, new String[]{var, arg});
    }

    /**
     * @return the Brigadier argument-type expression (e.g. {@code IntegerArgumentType.integer()}).
     */
    public CodeBlock brigadierArgType(TypeName type, boolean greedy) {
        Entry e = entries.get(type.toString());
        return e == null ? null : e.brigadierArgType.apply(greedy);
    }

    /**
     * @return the Brigadier retrieval expression (e.g. {@code IntegerArgumentType.getInteger(ctx, argName)}).
     */
    public CodeBlock brigadierRetrieval(TypeName type, String argName) {
        Entry e = entries.get(type.toString());
        return e == null ? null : e.brigadierRetrieval.apply(argName);
    }

    /**
     * @return the suggest-method return CodeBlock, or {@code null} if no platform suggestion.
     */
    public CodeBlock suggestReturn(TypeName type, String argsVar, String currentVar) {
        Entry e = entries.get(type.toString());
        return e == null ? null : e.suggestReturn.apply(argsVar, currentVar);
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
        ClassName str = ClassName.get(String.class);

        // java.lang.String
        register(entry(str, 1)
                .primitiveDefault("null")
                .literal(d -> CodeBlock.of("$S", d == null ? "" : d))
                .parse((spec, va) -> spec.addStatement("$L = $L", va[0], va[1]))
                .brigadierArgType(greedy -> CodeBlock.of("$T.$L()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"),
                        greedy ? "greedyString" : "string"))
                .brigadierRetrieval(arg -> CodeBlock.of("$T.getString(ctx, $S)",
                        ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), arg))
                .build());

        // int / Integer
        register(integerEntry(TypeName.INT, integer, "0", "integer", "IntegerArgumentType", "getInteger", "parseInt").build());
        register(integerEntry(integer, integer, "0", "integer", "IntegerArgumentType", "getInteger", "parseInt").build());
        // long / Long
        register(integerEntry(TypeName.LONG, longC, "0", "longArg", "LongArgumentType", "getLong", "parseLong")
                .literal(d -> CodeBlock.of("$LL", defaultTo(d, "0"))).build());
        register(integerEntry(longC, longC, "0", "longArg", "LongArgumentType", "getLong", "parseLong")
                .literal(d -> CodeBlock.of("$LL", defaultTo(d, "0"))).build());
        // double / Double
        register(integerEntry(TypeName.DOUBLE, dbl, "0.0", "doubleArg", "DoubleArgumentType", "getDouble", "parseDouble").build());
        register(integerEntry(dbl, dbl, "0.0", "doubleArg", "DoubleArgumentType", "getDouble", "parseDouble").build());
        // float / Float
        Entry.Builder floatPrim = integerEntry(TypeName.FLOAT, flt, "0.0", "floatArg", "FloatArgumentType", "getFloat", "parseFloat");
        register(floatPrim.literal(d -> CodeBlock.of("$Lf", defaultTo(d, "0.0"))).build());
        Entry.Builder floatWrap = integerEntry(flt, flt, "0.0", "floatArg", "FloatArgumentType", "getFloat", "parseFloat");
        register(floatWrap.literal(d -> CodeBlock.of("$Lf", defaultTo(d, "0.0"))).build());
        // short / Short
        Entry.Builder shortPrim = integerEntry(TypeName.SHORT, shrt, "0", null, null, null, "parseShort");
        register(shortPrim.literal(d -> CodeBlock.of("(short) $L", defaultTo(d, "0"))).build());
        Entry.Builder shortWrap = integerEntry(shrt, shrt, "0", null, null, null, "parseShort");
        register(shortWrap.literal(d -> CodeBlock.of("(short) $L", defaultTo(d, "0"))).build());
        // byte / Byte
        Entry.Builder bytePrim = integerEntry(TypeName.BYTE, byt, "0", null, null, null, "parseByte");
        register(bytePrim.literal(d -> CodeBlock.of("(byte) $L", defaultTo(d, "0"))).build());
        Entry.Builder byteWrap = integerEntry(byt, byt, "0", null, null, null, "parseByte");
        register(byteWrap.literal(d -> CodeBlock.of("(byte) $L", defaultTo(d, "0"))).build());
        // char / Character
        register(entry(chr, 1)
                .primitiveDefault("'\\0'")
                .literal(d -> {
                    char c = (d == null || d.isEmpty()) ? ' ' : d.charAt(0);
                    return CodeBlock.of("'$L'", c);
                })
                .parse((spec, va) -> {
                    spec.addStatement("if ($L.length() != 1) throw new $T($S + $L)", va[1], IllegalArgumentException.class, "Invalid character: ", va[1]);
                    spec.addStatement("$L = $L.charAt(0)", va[0], va[1]);
                })
                .build());
        register(entry(TypeName.CHAR, 1)
                .primitiveDefault("'\\0'")
                .literal(d -> {
                    char c = (d == null || d.isEmpty()) ? ' ' : d.charAt(0);
                    return CodeBlock.of("'$L'", c);
                })
                .parse((spec, va) -> {
                    spec.addStatement("if ($L.length() != 1) throw new $T($S + $L)", va[1], IllegalArgumentException.class, "Invalid character: ", va[1]);
                    spec.addStatement("$L = $L.charAt(0)", va[0], va[1]);
                })
                .build());
        // boolean / Boolean
        ClassName boolArg = ClassName.get("com.mojang.brigadier.arguments", "BoolArgumentType");
        register(entry(bool, 1)
                .primitiveDefault("false")
                .literal(d -> CodeBlock.of("$L", defaultTo(d, "false")))
                .parse((spec, va) -> {
                    spec.addStatement("if (!$S.equalsIgnoreCase($L) && !$S.equalsIgnoreCase($L)) throw new $T($S + $L)",
                            "true", va[1], "false", va[1], IllegalArgumentException.class, "Invalid boolean value: ", va[1]);
                    spec.addStatement("$L = $T.parseBoolean($L)", va[0], Boolean.class, va[1]);
                })
                .brigadierArgType(g -> CodeBlock.of("$T.bool()", boolArg))
                .brigadierRetrieval(arg -> CodeBlock.of("$T.getBool(ctx, $S)", boolArg, arg))
                .build());
        register(entry(TypeName.BOOLEAN, 1)
                .primitiveDefault("false")
                .literal(d -> CodeBlock.of("$L", defaultTo(d, "false")))
                .parse((spec, va) -> {
                    spec.addStatement("if (!$S.equalsIgnoreCase($L) && !$S.equalsIgnoreCase($L)) throw new $T($S + $L)",
                            "true", va[1], "false", va[1], IllegalArgumentException.class, "Invalid boolean value: ", va[1]);
                    spec.addStatement("$L = $T.parseBoolean($L)", va[0], Boolean.class, va[1]);
                })
                .brigadierArgType(g -> CodeBlock.of("$T.bool()", boolArg))
                .brigadierRetrieval(arg -> CodeBlock.of("$T.getBool(ctx, $S)", boolArg, arg))
                .build());
    }

    /**
     * Builder for the int/long/double/float/short/byte family (they only differ by parse method + literal).
     */
    private Entry.Builder integerEntry(TypeName keyType, ClassName wrapper, String defaultVal,
                                       String brigMethod, String brigArgClass, String brigRetrieval,
                                       String parseMethod) {
        Entry.Builder b = entry(keyType, 1)
                .primitiveDefault(defaultVal)
                .literal(d -> CodeBlock.of("$L", defaultTo(d, defaultVal)))
                .parse((spec, va) -> spec.addStatement("$L = $T.$L($L)", va[0], wrapper, parseMethod, va[1]));
        if (brigArgClass != null) {
            ClassName argClass = ClassName.get("com.mojang.brigadier.arguments", brigArgClass);
            b.brigadierArgType(g -> CodeBlock.of("$T.$L()", argClass, brigMethod))
                    .brigadierRetrieval(arg -> CodeBlock.of("$T.$L(ctx, $S)", argClass, brigRetrieval, arg));
        }
        return b;
    }

    /**
     * A registered type's behaviors. Immutable once built.
     */
    public static final class Entry {
        public final TypeName type;
        public final int width;
        final String primitiveDefault;
        final Function<String, CodeBlock> literal;
        final BiConsumer<MethodSpec.Builder, String[]> parse;
        final Function<Boolean, CodeBlock> brigadierArgType;
        final Function<String, CodeBlock> brigadierRetrieval;
        final BiFunction<String, String, CodeBlock> suggestReturn;

        private Entry(Builder b) {
            this.type = b.type;
            this.width = b.width;
            this.primitiveDefault = b.primitiveDefault;
            this.literal = b.literal;
            this.parse = b.parse;
            this.brigadierArgType = b.brigadierArgType;
            this.brigadierRetrieval = b.brigadierRetrieval;
            this.suggestReturn = b.suggestReturn;
        }

        static Builder builder(TypeName type, int width) {
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
            BiConsumer<MethodSpec.Builder, String[]> parse;
            Function<Boolean, CodeBlock> brigadierArgType;
            Function<String, CodeBlock> brigadierRetrieval;
            BiFunction<String, String, CodeBlock> suggestReturn;

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

            public Builder parse(BiConsumer<MethodSpec.Builder, String[]> f) {
                this.parse = f;
                return this;
            }

            public Builder brigadierArgType(Function<Boolean, CodeBlock> f) {
                this.brigadierArgType = f;
                return this;
            }

            public Builder brigadierRetrieval(Function<String, CodeBlock> f) {
                this.brigadierRetrieval = f;
                return this;
            }

            public Builder suggestReturn(BiFunction<String, String, CodeBlock> f) {
                this.suggestReturn = f;
                return this;
            }

            public Entry build() {
                return new Entry(this);
            }
        }
    }
}
