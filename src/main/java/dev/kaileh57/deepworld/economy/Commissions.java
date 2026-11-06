package dev.kaileh57.deepworld.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class Commissions extends SavedData {
	public record Commission(int id, String poster, String poster_name, String wanted, int count, List<ItemStack> reward, String acceptor, String acceptor_name, boolean npc) {
		public static final Codec<Commission> codec = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Commission::id),
			Codec.STRING.fieldOf("poster").forGetter(Commission::poster),
			Codec.STRING.fieldOf("poster_name").forGetter(Commission::poster_name),
			Codec.STRING.fieldOf("wanted").forGetter(Commission::wanted),
			Codec.INT.fieldOf("count").forGetter(Commission::count),
			ItemStack.OPTIONAL_CODEC.listOf().fieldOf("reward").forGetter(Commission::reward),
			Codec.STRING.fieldOf("acceptor").forGetter(Commission::acceptor),
			Codec.STRING.fieldOf("acceptor_name").forGetter(Commission::acceptor_name),
			Codec.BOOL.fieldOf("npc").forGetter(Commission::npc)
		).apply(i, Commission::new));

		public Item wanted_item() {
			return BuiltInRegistries.ITEM.getValue(Identifier.parse(wanted));
		}

		public Commission accepted_by(String uuid, String name) {
			return new Commission(id, poster, poster_name, wanted, count, reward, uuid, name, npc);
		}
	}

	public List<Commission> open = new ArrayList<>();
	public Map<String, List<ItemStack>> mail = new HashMap<>();
	public int next_id = 1;

	public static final Codec<Commissions> codec = RecordCodecBuilder.create(i -> i.group(
		Commission.codec.listOf().fieldOf("open").forGetter(c -> c.open),
		Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf()).fieldOf("mail").forGetter(c -> c.mail),
		Codec.INT.fieldOf("next_id").forGetter(c -> c.next_id)
	).apply(i, (open, mail, next) -> {
		Commissions c = new Commissions();
		c.open = new ArrayList<>(open);
		c.mail = new HashMap<>();
		mail.forEach((key, value) -> c.mail.put(key, new ArrayList<>(value)));
		c.next_id = next;
		return c;
	}));
	public static final SavedDataType<Commissions> type = new SavedDataType<>(DeepWorld.id("commissions"), Commissions::new, codec, null);

	public static Commissions get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(type);
	}

	public void post(Commission commission) {
		open.add(commission);
		next_id++;
		setDirty();
	}

	public void send_mail(String uuid, ItemStack stack) {
		mail.computeIfAbsent(uuid, k -> new ArrayList<>()).add(stack);
		setDirty();
	}

	private static final String[][] npc_wants = {
		{"deepworld:raw_tin", "16"}, {"deepworld:rock_salt", "24"}, {"deepworld:resonant_shard", "8"}, {"deepworld:mantle_iron_chunk", "8"},
		{"deepworld:raw_cobalt", "8"}, {"deepworld:glowcap", "12"}, {"deepworld:ember_root", "12"}, {"deepworld:hush_moss", "8"},
		{"minecraft:ancient_debris", "2"}, {"deepworld:blind_eel", "6"}, {"deepworld:steel_ingot", "8"}, {"deepworld:compost", "16"}
	};
	private static final String[][] npc_rewards = {
		{"minecraft:emerald", "8"}, {"minecraft:diamond", "2"}, {"deepworld:steel_ingot", "6"}, {"minecraft:gold_block", "1"},
		{"deepworld:salt", "16"}, {"minecraft:experience_bottle", "8"}, {"deepworld:growth_tonic", "4"}, {"deepworld:black_powder_charge", "6"}
	};

	private static final String[][] bounties = {
		{"deepworld:matriarch_fang", "1", "minecraft:diamond", "3"}, {"deepworld:rusted_helm", "1", "deepworld:steel_ingot", "8"},
		{"deepworld:crystal_heart", "1", "minecraft:emerald", "16"}, {"deepworld:ghast_trophy", "1", "minecraft:gold_block", "2"},
		{"deepworld:warden_trophy", "1", "minecraft:netherite_scrap", "1"}, {"deepworld:hoglin_tusk", "1", "minecraft:diamond", "4"},
		{"minecraft:ghast_tear", "3", "minecraft:emerald", "12"}, {"minecraft:echo_shard", "4", "minecraft:diamond", "3"}
	};

	public void tick_npc(MinecraftServer server, RandomSource random) {
		long npc_count = open.stream().filter(Commission::npc).count();
		if (npc_count >= 4) {
			return;
		}
		boolean has_bounty = open.stream().anyMatch(c -> c.npc() && c.poster_name().equals("the hunters' lodge"));
		if (!has_bounty && random.nextFloat() < 0.5F) {
			String[] bounty = bounties[random.nextInt(bounties.length)];
			List<ItemStack> prize = new ArrayList<>();
			prize.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(bounty[2])), Integer.parseInt(bounty[3])));
			post(new Commission(next_id, "", "the hunters' lodge", bounty[0], Integer.parseInt(bounty[1]), prize, "", "", true));
			return;
		}
		String[] want = npc_wants[random.nextInt(npc_wants.length)];
		String[] give = npc_rewards[random.nextInt(npc_rewards.length)];
		List<ItemStack> reward = new ArrayList<>();
		reward.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(give[0])), Integer.parseInt(give[1])));
		post(new Commission(next_id, "", "the deepline co-op", want[0], Integer.parseInt(want[1]), reward, "", "", true));
	}
}
