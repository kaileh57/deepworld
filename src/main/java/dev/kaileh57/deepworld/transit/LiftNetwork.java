package dev.kaileh57.deepworld.transit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kaileh57.deepworld.DeepWorld;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class LiftNetwork extends SavedData {
	public Map<String, Integer> cable = new HashMap<>();
	public Map<String, Integer> car = new HashMap<>();
	public static final int blocks_per_cable = 64;
	public static final Codec<LiftNetwork> codec = RecordCodecBuilder.create(i -> i.group(
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("cable", Map.of()).forGetter(n -> n.cable),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("car", Map.of()).forGetter(n -> n.car)
	).apply(i, (cable, car) -> {
		LiftNetwork n = new LiftNetwork();
		n.cable.putAll(cable);
		n.car.putAll(car);
		return n;
	}));
	public static final SavedDataType<LiftNetwork> type = new SavedDataType<>(DeepWorld.id("lift_network"), LiftNetwork::new, codec, null);

	public static LiftNetwork get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(type);
	}

	public static String column(BlockPos pos) {
		return pos.getX() + " " + pos.getZ();
	}

	public int cable_at(BlockPos pos) {
		return cable.getOrDefault(column(pos), 0);
	}

	public void add_cable(BlockPos pos, int amount) {
		cable.merge(column(pos), amount, Integer::sum);
		setDirty();
	}

	public boolean reaches(BlockPos from, BlockPos to) {
		return Math.abs(from.getY() - to.getY()) <= cable_at(from) * blocks_per_cable;
	}

	public int car_y(BlockPos at) {
		return car.computeIfAbsent(column(at), key -> {
			setDirty();
			return at.getY();
		});
	}

	public void move_car(BlockPos to) {
		car.put(column(to), to.getY());
		setDirty();
	}
}
