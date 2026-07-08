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
                .build());

        // int / Integer
        register(integerEntry(TypeName.INT, integer, "0", "parseInt").build());
        register(integerEntry(integer, integer, "0", "parseInt").build());
        // long / Long
        register(integerEntry(TypeName.LONG, longC, "0", "parseLong")
                .literal(d -> CodeBlock.of("$LL", defaultTo(d, "0"))).build());
        register(integerEntry(longC, longC, "0", "parseLong")
                .literal(d -> CodeBlock.of("$LL", defaultTo(d, "0"))).build());
        // double / Double
        register(integerEntry(TypeName.DOUBLE, dbl, "0.0", "parseDouble").build());
        register(integerEntry(dbl, dbl, "0.0", "parseDouble").build());
        // float / Float
        Entry.Builder floatPrim = integerEntry(TypeName.FLOAT, flt, "0.0", "parseFloat");
        register(floatPrim.literal(d -> CodeBlock.of("$Lf", defaultTo(d, "0.0"))).build());
        Entry.Builder floatWrap = integerEntry(flt, flt, "0.0", "parseFloat");
        register(floatWrap.literal(d -> CodeBlock.of("$Lf", defaultTo(d, "0.0"))).build());
        // short / Short
        Entry.Builder shortPrim = integerEntry(TypeName.SHORT, shrt, "0", "parseShort");
        register(shortPrim.literal(d -> CodeBlock.of("(short) $L", defaultTo(d, "0"))).build());
        Entry.Builder shortWrap = integerEntry(shrt, shrt, "0", "parseShort");
        register(shortWrap.literal(d -> CodeBlock.of("(short) $L", defaultTo(d, "0"))).build());
        // byte / Byte
        Entry.Builder bytePrim = integerEntry(TypeName.BYTE, byt, "0", "parseByte");
        register(bytePrim.literal(d -> CodeBlock.of("(byte) $L", defaultTo(d, "0"))).build());
        Entry.Builder byteWrap = integerEntry(byt, byt, "0", "parseByte");
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
        register(entry(bool, 1)
                .primitiveDefault("false")
                .literal(d -> CodeBlock.of("$L", defaultTo(d, "false")))
                .parse((spec, va) -> {
                    spec.addStatement("if (!$S.equalsIgnoreCase($L) && !$S.equalsIgnoreCase($L)) throw new $T($S + $L)",
                            "true", va[1], "false", va[1], IllegalArgumentException.class, "Invalid boolean value: ", va[1]);
                    spec.addStatement("$L = $T.parseBoolean($L)", va[0], Boolean.class, va[1]);
                })
                .build());
        register(entry(TypeName.BOOLEAN, 1)
                .primitiveDefault("false")
                .literal(d -> CodeBlock.of("$L", defaultTo(d, "false")))
                .parse((spec, va) -> {
                    spec.addStatement("if (!$S.equalsIgnoreCase($L) && !$S.equalsIgnoreCase($L)) throw new $T($S + $L)",
                            "true", va[1], "false", va[1], IllegalArgumentException.class, "Invalid boolean value: ", va[1]);
                    spec.addStatement("$L = $T.parseBoolean($L)", va[0], Boolean.class, va[1]);
                })
                .build());
    }

    /**
     * Builder for the int/long/double/float/short/byte family (they only differ by parse method).
     */
    private Entry.Builder integerEntry(TypeName keyType, ClassName wrapper, String defaultVal, String parseMethod) {
        return entry(keyType, 1)
                .primitiveDefault(defaultVal)
                .literal(d -> CodeBlock.of("$L", defaultTo(d, defaultVal)))
                .parse((spec, va) -> spec.addStatement("$L = $T.$L($L)", va[0], wrapper, parseMethod, va[1]));
    }

    /**
     * Emit platform-specific resolution code (e.g. {@code var = getPlayer(arg)}).
     *
     * @param spec   the method builder
     * @param params [varName, argsVar, argIdxVar, senderVar, index]
     */
    public void emitPlatformResolution(MethodSpec.Builder spec, TypeName type, String... params) {
        Entry e = entries.get(type.toString());
        if (e == null || e.platformResolution == null) {
            return;
        }
        e.platformResolution.accept(spec, params);
    }

    /**
     * Emit platform-specific multi-arg resolution code (e.g. {@code var = getLocation(args, argIdx)}).
     *
     * @param params [varName, argsVar, argIdxVar, senderVar, index]
     */
    public void emitPlatformMultiResolution(MethodSpec.Builder spec, TypeName type, String... params) {
        Entry e = entries.get(type.toString());
        if (e == null || e.platformMultiResolution == null) {
            return;
        }
        e.platformMultiResolution.accept(spec, params);
    }

    /**
     * @return the platform-specific width for multi-arg types (e.g. Location=4), defaulting to 1.
     */
    public int getPlatformWidth(TypeName type) {
        Entry e = entries.get(type.toString());
        return e == null ? 1 : e.platformWidth;
    }

    /**
     * Emit platform-specific suggestion code.
     *
     * @param params [senderCastVar, argsVar, currentVar, tempIdx]
     */
    public void emitPlatformSuggestions(MethodSpec.Builder spec, TypeName type, String... params) {
        Entry e = entries.get(type.toString());
        if (e == null || e.platformSuggestions == null) {
            return;
        }
        e.platformSuggestions.accept(spec, params);
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
        final BiFunction<String, String, CodeBlock> suggestReturn;
        final int platformWidth;
        final BiConsumer<MethodSpec.Builder, String[]> platformResolution;
        final BiConsumer<MethodSpec.Builder, String[]> platformMultiResolution;
        final BiConsumer<MethodSpec.Builder, String[]> platformSuggestions;

        private Entry(Builder b) {
            this.type = b.type;
            this.width = b.width;
            this.primitiveDefault = b.primitiveDefault;
            this.literal = b.literal;
            this.parse = b.parse;
            this.suggestReturn = b.suggestReturn;
            this.platformWidth = b.platformWidth;
            this.platformResolution = b.platformResolution;
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
            BiConsumer<MethodSpec.Builder, String[]> parse;
            BiFunction<String, String, CodeBlock> suggestReturn;
            int platformWidth = 1;
            BiConsumer<MethodSpec.Builder, String[]> platformResolution;
            BiConsumer<MethodSpec.Builder, String[]> platformMultiResolution;
            BiConsumer<MethodSpec.Builder, String[]> platformSuggestions;

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

            public Builder suggestReturn(BiFunction<String, String, CodeBlock> f) {
                this.suggestReturn = f;
                return this;
            }

            public Builder platformWidth(int w) {
                this.platformWidth = w;
                return this;
            }

            public Builder platformResolution(BiConsumer<MethodSpec.Builder, String[]> f) {
                this.platformResolution = f;
                return this;
            }

            public Builder platformMultiResolution(BiConsumer<MethodSpec.Builder, String[]> f) {
                this.platformMultiResolution = f;
                return this;
            }

            public Builder platformSuggestions(BiConsumer<MethodSpec.Builder, String[]> f) {
                this.platformSuggestions = f;
                return this;
            }

            public Entry build() {
                return new Entry(this);
            }
        }
    }
}
