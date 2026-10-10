package me.noramibu.tweaks.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;


public class MobCheckerCommand extends Command {
	private static final int DEFAULT_RANGE = 64;

	public MobCheckerCommand() {
		super("mobchecker", "Count nearby mobs. Usage: .mobchecker [range] [filter]");
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
    public void build(LiteralArgumentBuilder builder) {
		builder.executes(ctx -> {
			runCheck(DEFAULT_RANGE, null);
			return SINGLE_SUCCESS;
		});

		builder.then(argument("range", IntegerArgumentType.integer(1, 1024)).executes(ctx -> {
			int range = IntegerArgumentType.getInteger(ctx, "range");
			runCheck(range, null);
			return SINGLE_SUCCESS;
		}).then(argument("filter", StringArgumentType.word()).executes(ctx -> {
			int range = IntegerArgumentType.getInteger(ctx, "range");
			String filter = StringArgumentType.getString(ctx, "filter");
			runCheck(range, emptyToNull(filter));
			return SINGLE_SUCCESS;
		})));

		builder.then(argument("filter", StringArgumentType.word()).executes(ctx -> {
			String filter = StringArgumentType.getString(ctx, "filter");
			runCheck(DEFAULT_RANGE, emptyToNull(filter));
			return SINGLE_SUCCESS;
		}));
	}

	private static String emptyToNull(String s) {
		return s == null || s.isEmpty() ? null : s;
	}

	private void runCheck(int range, String mobTypeFilter) {
		if (mc.player == null || mc.level == null) return;

		Map<String, Integer> entityCounts = new HashMap<>();
		final int rangeSq = range * range;
		final String filterLower = (mobTypeFilter == null || mobTypeFilter.isEmpty()) ? null : mobTypeFilter.toLowerCase();

		Vec3 center = mc.player.position();
		AABB box = AABB.ofSize(center, range * 2.0, range * 2.0, range * 2.0);

		mc.level.getEntities((Entity) null, box, entity -> true).forEach(entity -> {
			if (entity.distanceToSqr(mc.player) > (double) rangeSq) return;
			if (filterLower != null) {
				Identifier entityTypeId = EntityType.getKey(entity.getType());
				if (entityTypeId == null || !entityTypeId.getPath().toLowerCase().contains(filterLower)) return;
			}

			String name = entity.getType().getDescription().getString();
			if (entity instanceof AgeableMob && ((AgeableMob) entity).isBaby()) name += " (Baby)";
			entityCounts.merge(name, 1, Integer::sum);
		});

		if (entityCounts.isEmpty()) {
			if (mobTypeFilter != null && !mobTypeFilter.isEmpty()) info("No '" + mobTypeFilter + "' found within " + range + " blocks.");
			else info("No mobs found within " + range + " blocks.");
			return;
		}

		if (mobTypeFilter != null && !mobTypeFilter.isEmpty()) info("Found matching '" + mobTypeFilter + "' within " + range + " blocks:");
		else info("Mobs found within " + range + " blocks:");

		entityCounts.entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
			.forEach(e -> info("- " + e.getKey() + ": " + e.getValue()));

		if (entityCounts.size() > 1) {
			int totalSum = entityCounts.values().stream().mapToInt(Integer::intValue).sum();
			info("Total: " + totalSum);
		}
	}
}
