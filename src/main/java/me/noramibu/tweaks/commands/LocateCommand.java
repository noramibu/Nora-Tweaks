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
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import me.noramibu.tweaks.utils.WorldGenUtils;
import me.noramibu.tweaks.utils.Seeds;
import me.noramibu.tweaks.utils.Seeds.Seed;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

public class LocateCommand extends Command {
    private static final DynamicCommandExceptionType NOT_FOUND = new DynamicCommandExceptionType(o -> {
        if (o instanceof WorldGenUtils.Structure type) {
            return Component.literal(String.format("%s not found.", Utils.nameToTitle(type.commandName.replace('_', '-'))));
        }
        return Component.literal("Not found.");
    });
    private static final DynamicCommandExceptionType INVALID_FEATURE = new DynamicCommandExceptionType(o ->
        Component.literal(String.format("%s is not a valid feature.", o))
    );

    public LocateCommand() {
        super("seed-locate", "Locate structures using the stored seed.", "seed-loc");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void build(LiteralArgumentBuilder builder) {
        builder.then(literal("feature")
            .then(argument("feature", StringArgumentType.word())
                .suggests((ctx, builder1) -> SharedSuggestionProvider.suggest(
                    WorldGenUtils.structureNames(),
                    builder1
                ))
                .executes(ctx -> {
                    WorldGenUtils.Structure feature = parseFeature(StringArgumentType.getString(ctx, "feature"));
                if (mc.player == null) return SINGLE_SUCCESS;

                BlockPos playerPos = mc.player.blockPosition();
                Seed seed = Seeds.get().getSeed();
                if (seed == null) throw NOT_FOUND.create(feature);

                BlockPos located = WorldGenUtils.locateNearestStructure(feature, playerPos, seed);
                if (located == null) located = WorldGenUtils.locateFeature(feature, playerPos);
                if (located == null) throw NOT_FOUND.create(feature);

                int distance = (int) Math.hypot(located.getX() - playerPos.getX(), located.getZ() - playerPos.getZ());
                MutableComponent text = Component.literal(String.format("%s located at ", Utils.nameToTitle(feature.commandName.replace('_', '-'))));
                text.append(ChatUtils.formatCoords(new Vec3(located.getX(), 0, located.getZ())));
                text.append(".");
                if (distance > 0) {
                    text.append(String.format(" (%d blocks away)", distance));
                }
                info(text);
                return SINGLE_SUCCESS;
            })));
    }

    private static WorldGenUtils.Structure parseFeature(String input) throws CommandSyntaxException {
        WorldGenUtils.Structure structure = WorldGenUtils.parseStructure(input);
        if (structure != null) return structure;
        throw INVALID_FEATURE.create(input);
    }
}
