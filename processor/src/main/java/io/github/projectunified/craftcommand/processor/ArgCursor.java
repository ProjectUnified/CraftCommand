package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.MethodSpec;

/**
 * Emits the argument access expressions of one generated command slot.
 *
 * <p>While nothing has consumed a variable number of arguments the cursor renders constant indices, so the
 * generated code reads {@code args[1]} instead of advancing a variable. Once a parameter may consume
 * conditionally, or a runtime resolver may consume a variable number of arguments, the cursor declares a local
 * cursor and renders every later read through it.
 */
final class ArgCursor {
    private static final String INDEX_VARIABLE = "argIdx";
    private static final String SHARED_VARIABLE = "argIdxHolder";

    private final MethodSpec.Builder methodSpec;
    private final String argsVar;
    private final boolean shared;
    private int offset;
    private boolean declared;

    /**
     * Constructs a cursor positioned at the first argument of a command slot.
     *
     * @param methodSpec         the method being generated
     * @param argsVar            the name of the {@code String[]} argument array
     * @param initialOffset      the index of the first command argument
     * @param mayConsumeVariably whether a runtime resolver may consume a variable number of arguments
     */
    ArgCursor(MethodSpec.Builder methodSpec, String argsVar, int initialOffset, boolean mayConsumeVariably) {
        this.methodSpec = methodSpec;
        this.argsVar = argsVar;
        this.offset = initialOffset;
        this.shared = mayConsumeVariably;
    }

    /**
     * The name of the {@code String[]} argument array.
     *
     * @return the argument array variable
     */
    String argsVariable() {
        return argsVar;
    }

    /**
     * Reads the current argument and consumes it.
     *
     * @return the argument expression
     */
    String take() {
        if (declared) {
            return argsVar + "[" + cursorRef() + "++]";
        }
        return argsVar + "[" + offset++ + "]";
    }

    /**
     * Reads every remaining argument, starting at the current one.
     *
     * @return the slice expression
     */
    String sliceFromHere() {
        return "Arrays.copyOfRange(" + argsVar + ", " + index() + ", " + argsVar + ".length)";
    }

    /**
     * Guards a parameter that needs a fixed number of arguments.
     *
     * @param width the number of arguments the parameter consumes
     * @return an expression that is true when too few arguments remain
     */
    String notEnoughArguments(int width) {
        if (declared) {
            return cursorRef() + " + " + width + " > " + argsVar + ".length";
        }
        return argsVar + ".length < " + (offset + width);
    }

    /**
     * The expression for the current argument index.
     *
     * @return a constant index, or the local cursor expression
     */
    String index() {
        return declared ? cursorRef() : String.valueOf(offset);
    }

    /**
     * Consumes arguments that were read at once, such as a multi-argument platform type.
     *
     * @param width the number of consumed arguments
     */
    void advance(int width) {
        if (declared) {
            methodSpec.addStatement("$L += $L", cursorRef(), width);
        } else {
            offset += width;
        }
    }

    /**
     * Ensures a local cursor exists at the current offset, for a parameter that may be consumed conditionally.
     */
    void requireIndex() {
        if (declared) {
            return;
        }
        methodSpec.addStatement(shared ? "int[] $L = { $L }" : "int $L = $L",
                shared ? SHARED_VARIABLE : INDEX_VARIABLE, offset);
        declared = true;
    }

    /**
     * The cursor to hand to {@code CommandManager.resolveParameter}.
     *
     * <p>When a parameter may be consumed variably, all resolvers share one cursor so their consumption is
     * visible to the parameters that follow.
     *
     * @return the cursor expression
     */
    String runtimeCursor() {
        if (!shared) {
            return "new int[] { " + index() + " }";
        }
        requireIndex();
        return SHARED_VARIABLE;
    }

    /**
     * @return whether the current offset is tracked by a local cursor
     */
    boolean isDeclared() {
        return declared;
    }

    private String cursorRef() {
        return shared ? SHARED_VARIABLE + "[0]" : INDEX_VARIABLE;
    }
}
