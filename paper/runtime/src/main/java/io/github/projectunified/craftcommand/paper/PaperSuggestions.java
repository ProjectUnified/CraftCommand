package io.github.projectunified.craftcommand.paper;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.projectunified.craftcommand.CommandManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Arrays;
import java.util.Collection;
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
        for (String suggestion : CommandManager.filterSuggestions(
                provider.apply(arguments(ctx)), builder.getRemaining())) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

    /**
     * The parsed command arguments, dropping the leading command label when present.
     *
     * @param ctx the Brigadier command context
     * @return the command arguments
     */
    public static String[] arguments(CommandContext<CommandSourceStack> ctx) {
        String[] args = ctx.getInput().split(" ", -1);
        return Arrays.copyOfRange(args, 1, args.length);
    }
}
