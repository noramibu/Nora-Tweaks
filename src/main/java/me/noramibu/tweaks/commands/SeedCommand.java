/*
 * This code partially adapted from Meteor Rejects
 * Original source: https://github.com/AntiCope/meteor-rejects/
 * Credit: Meteor Rejects contributors
 * If Meteor Rejects gets updated, adapted features will get removed.
 */
package me.noramibu.tweaks.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.noramibu.tweaks.utils.Seeds;
import me.noramibu.tweaks.utils.Seeds.Seed;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class SeedCommand extends Command {
    private static final SimpleCommandExceptionType NO_SEED = new SimpleCommandExceptionType(Component.literal("No seed for current world saved."));
    private static final SimpleCommandExceptionType INVALID_VERSION = new SimpleCommandExceptionType(Component.literal("Unknown Minecraft version."));

    public SeedCommand() {
        super("seed-world", "Get or set the seed for the current world.");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void build(LiteralArgumentBuilder builder) {
        builder.executes(ctx -> {
            Seed seed = Seeds.get().getSeed();
            if (seed == null) throw NO_SEED.create();
            info(seed.toText());
            return SINGLE_SUCCESS;
        });

        builder.then(literal("list").executes(ctx -> {
            Seeds.get().seeds.forEach((name, storedSeed) -> {
                if (storedSeed == null) return;
                MutableComponent text = Component.literal(name + " ");
                text.append(storedSeed.toText());
                info(text);
            });
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("delete").executes(ctx -> {
            Seed seed = Seeds.get().getSeed();
            if (seed != null) {
                MutableComponent text = Component.literal("Deleted ");
                text.append(seed.toText());
                info(text);
            }
            Seeds.get().removeSeed(Utils.getWorldName());
            return SINGLE_SUCCESS;
        }));

        builder.then(argument("seed", StringArgumentType.string()).executes(ctx -> {
            Seeds.get().setSeed(StringArgumentType.getString(ctx, "seed"));
            return SINGLE_SUCCESS;
        }));

        builder.then(
            argument("seed", StringArgumentType.string())
                .then(argument("version", StringArgumentType.word())
                    .suggests((ctx, builder1) -> SharedSuggestionProvider.suggest(
                        Seeds.getSuggestedCubiomesVersions(),
                        builder1
                    ))
                    .executes(ctx -> {
                        Seeds.get().setSeed(
                            StringArgumentType.getString(ctx, "seed"),
                            parseVersion(StringArgumentType.getString(ctx, "version"))
                        );
                        return SINGLE_SUCCESS;
                    }))
        );
    }

    private static String parseVersion(String input) throws CommandSyntaxException {
        String version = Seeds.resolveForPublic(input);
        if (version == null) throw INVALID_VERSION.create();
        return version;
    }
}
