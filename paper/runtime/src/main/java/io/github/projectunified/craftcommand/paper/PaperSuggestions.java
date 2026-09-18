package io.github.projectunified.craftcommand.paper;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Brigadier suggestion support shared by every generated Paper command wrapper.
 *
 * <p>Keeping the input handling here means a generated wrapper only states which provider answers for an
 * argument, instead of repeating the argument splitting and filtering for every command.
 */
public final class PaperSuggestions {
    private PaperSuggestions() {
    }

    /**
     * Completes the argument the sender is currently typing from a suggestion provider.
     *
     * @param ctx      the Brigadier command context
     * @param builder  the suggestions builder holding the remaining input
     * @param provider supplies the candidate suggestions for the parsed arguments
     * @return the suggestions for the current input
     */
    public static CompletableFuture<Suggestions> suggestMatching(CommandContext<CommandSourceStack> ctx,
                                                                SuggestionsBuilder builder,
                                                                Function<String[], Collection<String>> provider) {
        String remainingLower = builder.getRemaining().toLowerCase();
        for (String suggestion : provider.apply(splitArgs(ctx.getInput(), ctx.getRootNode().getName()))) {
            if (remainingLower.isEmpty() || suggestion.toLowerCase().startsWith(remainingLower)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    /**
     * Splits raw command input into arguments, dropping the leading command label when present.
     *
     * @param input    the raw input line
     * @param rootName the root command name, used to recognise an explicit label
     * @return the command arguments
     */
    public static String[] splitArgs(String input, String rootName) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ' ') {
                args.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        args.add(current.toString());
        if (!args.isEmpty() && (args.get(0).startsWith("/") || args.get(0).equalsIgnoreCase(rootName))) {
            args.remove(0);
        }
        return args.toArray(new String[0]);
    }
}
