package dev.kaileh57.deepworld.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.kaileh57.deepworld.world.Layers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class SurveyCommand {
	private SurveyCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("dw").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("survey")
				.executes(context -> survey(context, 0, 0, 3))
				.then(Commands.argument("chunk_x", IntegerArgumentType.integer())
					.then(Commands.argument("chunk_z", IntegerArgumentType.integer())
						.then(Commands.argument("radius", IntegerArgumentType.integer(0, 8))
							.executes(context -> survey(context, IntegerArgumentType.getInteger(context, "chunk_x"), IntegerArgumentType.getInteger(context, "chunk_z"), IntegerArgumentType.getInteger(context, "radius")))))))
			.then(Commands.literal("fortress").executes(SurveyCommand::fortress))
			.then(Commands.literal("map")
				.then(Commands.argument("x", IntegerArgumentType.integer())
					.then(Commands.argument("z", IntegerArgumentType.integer())
						.then(Commands.argument("size", IntegerArgumentType.integer(16, 1024))
							.executes(context -> map(context, IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "z"), IntegerArgumentType.getInteger(context, "size")))))))
			.then(Commands.literal("selftest").executes(context -> {
				java.util.List<String> lines = SelfTest.run(context.getSource().getServer());
				try {
					Files.writeString(context.getSource().getServer().getServerDirectory().resolve("deepworld_selftest.txt"), String.join("\n", lines) + "\n");
				} catch (Exception error) {
					context.getSource().sendFailure(Component.literal("could not write selftest"));
				}
				for (String line : lines) {
					context.getSource().sendSuccess(() -> Component.literal(line + "\n"), false);
				}
				return 1;
			}))
			.then(Commands.literal("slice")
				.then(Commands.argument("z", IntegerArgumentType.integer())
					.then(Commands.argument("x_from", IntegerArgumentType.integer())
						.then(Commands.argument("width", IntegerArgumentType.integer(16, 1024))
							.executes(context -> slice(context, IntegerArgumentType.getInteger(context, "z"), IntegerArgumentType.getInteger(context, "x_from"), IntegerArgumentType.getInteger(context, "width"))))))));
	}

	private static int survey(CommandContext<CommandSourceStack> context, int center_x, int center_z, int radius) {
		ServerLevel level = context.getSource().getServer().overworld();
		Map<String, long[]> counts = new TreeMap<>();
		Map<String, TreeSet<String>> biomes = new HashMap<>();
		Map<String, Map<String, Integer>> ores = new HashMap<>();
		for (int chunk_x = center_x - radius; chunk_x <= center_x + radius; chunk_x++) {
			for (int chunk_z = center_z - radius; chunk_z <= center_z + radius; chunk_z++) {
				ChunkAccess chunk = level.getChunk(chunk_x, chunk_z);
				ChunkPos pos = chunk.getPos();
				BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
				for (int y = level.getMinY(); y < level.getMaxY(); y++) {
					Layers.Layer layer = Layers.at(y);
					String name = layer == null ? "surface" : layer.name();
					long[] tally = counts.computeIfAbsent(name, key -> new long[4]);
					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							cursor.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
							BlockState state = chunk.getBlockState(cursor);
							tally[0]++;
							if (state.isAir()) {
								tally[1]++;
							} else if (state.is(Blocks.WATER)) {
								tally[2]++;
							} else if (state.is(Blocks.LAVA)) {
								tally[3]++;
							} else if (layer != null) {
								String block_id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
								if (layer.ores().contains(block_id)) {
									ores.computeIfAbsent(name, key -> new TreeMap<>()).merge(block_id, 1, Integer::sum);
								}
							}
							if (x % 4 == 0 && z % 4 == 0 && y % 4 == 0) {
								Holder<Biome> biome = level.getNoiseBiome((pos.getMinBlockX() + x) >> 2, y >> 2, (pos.getMinBlockZ() + z) >> 2);
								biomes.computeIfAbsent(name, key -> new TreeSet<>()).add(biome.unwrapKey().map(key -> key.identifier().toString()).orElse("?"));
							}
						}
					}
				}
			}
		}
		JsonObject report = new JsonObject();
		for (Map.Entry<String, long[]> entry : counts.entrySet()) {
			long[] tally = entry.getValue();
			JsonObject layer_report = new JsonObject();
			layer_report.addProperty("blocks", tally[0]);
			layer_report.addProperty("air", tally[1] / (double) tally[0]);
			layer_report.addProperty("water", tally[2] / (double) tally[0]);
			layer_report.addProperty("lava", tally[3] / (double) tally[0]);
			JsonArray biome_list = new JsonArray();
			biomes.getOrDefault(entry.getKey(), new TreeSet<>()).forEach(biome_list::add);
			layer_report.add("biomes", biome_list);
			JsonObject ore_counts = new JsonObject();
			ores.getOrDefault(entry.getKey(), Map.of()).forEach(ore_counts::addProperty);
			layer_report.add("ores", ore_counts);
			report.add(entry.getKey(), layer_report);
		}
		try {
			Path out = context.getSource().getServer().getServerDirectory().resolve("deepworld_survey.json");
			Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(report));
		} catch (Exception error) {
			context.getSource().sendFailure(Component.literal("could not write survey: " + error.getMessage()));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("survey done, " + counts.size() + " layers"), false);
		return 1;
	}

	private static int slice(CommandContext<CommandSourceStack> context, int z, int x_from, int width) {
		ServerLevel level = context.getSource().getServer().overworld();
		int height = level.getMaxY() - level.getMinY();
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = 0; x < width; x++) {
			ChunkAccess chunk = level.getChunk((x_from + x) >> 4, z >> 4);
			for (int y = level.getMinY(); y < level.getMaxY(); y++) {
				cursor.set(x_from + x, y, z);
				BlockState state = chunk.getBlockState(cursor);
				int color;
				if (state.isAir()) {
					color = 0x101014;
				} else if (state.is(Blocks.LAVA)) {
					color = 0xff6a00;
				} else if (state.is(Blocks.WATER)) {
					color = 0x2050c0;
				} else {
					color = state.getMapColor(level, cursor).col;
				}
				image.setRGB(x, level.getMaxY() - 1 - y, color);
			}
		}
		try {
			Path out = context.getSource().getServer().getServerDirectory().resolve("deepworld_slice.png");
			javax.imageio.ImageIO.write(image, "png", out.toFile());
		} catch (Exception error) {
			context.getSource().sendFailure(Component.literal("could not write slice: " + error.getMessage()));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("slice done"), false);
		return 1;
	}

	private static int map(CommandContext<CommandSourceStack> context, int center_x, int center_z, int size) {
		ServerLevel level = context.getSource().getServer().overworld();
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int x0 = center_x - size / 2;
		int z0 = center_z - size / 2;
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				ChunkAccess chunk = level.getChunk((x0 + x) >> 4, (z0 + z) >> 4);
				int top = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, (x0 + x) & 15, (z0 + z) & 15);
				cursor.set(x0 + x, top, z0 + z);
				BlockState state = chunk.getBlockState(cursor);
				int color = state.getMapColor(level, cursor).col;
				int shade = Math.max(0, Math.min(40, top - 1190));
				int r = Math.min(255, ((color >> 16) & 255) * (200 + shade) / 240);
				int g = Math.min(255, ((color >> 8) & 255) * (200 + shade) / 240);
				int b = Math.min(255, (color & 255) * (200 + shade) / 240);
				image.setRGB(x, z, (r << 16) | (g << 8) | b);
			}
		}
		try {
			javax.imageio.ImageIO.write(image, "png", context.getSource().getServer().getServerDirectory().resolve("deepworld_map.png").toFile());
		} catch (Exception error) {
			context.getSource().sendFailure(Component.literal("could not write map"));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("map done"), false);
		return 1;
	}

	private static int fortress(CommandContext<CommandSourceStack> context) {
		ServerLevel level = context.getSource().getServer().overworld();
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("fortress"));
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(holder), BlockPos.ZERO, 125, false);
		if (found == null) {
			context.getSource().sendSuccess(() -> Component.literal("fortress none"), false);
			return 0;
		}
		BlockPos pos = found.getFirst();
		ChunkAccess chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
		StructureStart start = chunk.getStartForStructure(holder.value());
		String box = start == null ? "?" : start.getBoundingBox().minY() + ".." + start.getBoundingBox().maxY();
		context.getSource().sendSuccess(() -> Component.literal("fortress " + pos.getX() + " " + pos.getZ() + " y " + box), false);
		return 1;
	}
}
