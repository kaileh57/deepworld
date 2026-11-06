package dev.kaileh57.deepworld.hunting;

import dev.kaileh57.deepworld.command.SelfTest;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.CookData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public final class HuntingTests {
	private static final BlockPos base = new BlockPos(2, 1530, 6);

	private HuntingTests() {
	}

	private static <T extends Mob> T spawn(ServerLevel level, net.minecraft.world.entity.EntityType<T> type) {
		T mob = type.create(level, EntitySpawnReason.COMMAND);
		mob.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
		mob.setNoAi(true);
		mob.addTag(Creatures.checked);
		level.addFreshEntity(mob);
		return mob;
	}

	private static int collect(ServerLevel level, net.minecraft.world.item.Item item) {
		int count = 0;
		for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(3))) {
			if (drop.getItem().is(item)) {
				count += drop.getItem().getCount();
			}
			drop.discard();
		}
		return count;
	}

	public static void register() {
		SelfTest.add("matriarchs are big and drop a fang", server -> {
			ServerLevel level = server.overworld();
			Mob spider = spawn(level, EntityTypes.CAVE_SPIDER);
			Creatures.make_matriarch(spider);
			double scale = spider.getAttributeValue(Attributes.SCALE);
			net.fabricmc.fabric.api.entity.FakePlayer hunter = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
			Creatures.drops(level, spider, hunter);
			int fangs = collect(level, Content.item("matriarch_fang"));
			spider.discard();
			return scale > 1.7 && fangs == 1 ? null : "scale " + scale + " fangs " + fangs;
		});
		SelfTest.add("armored husks wear iron", server -> {
			ServerLevel level = server.overworld();
			Mob husk = spawn(level, EntityTypes.HUSK);
			Creatures.make_armored(husk);
			boolean dressed = husk.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && husk.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE);
			Creatures.drops(level, husk, null);
			int nuggets = collect(level, Items.IRON_NUGGET);
			husk.discard();
			return dressed && nuggets >= 4 ? null : "dressed " + dressed + " nuggets " + nuggets;
		});
		SelfTest.add("pigs learn to hunt truffles", server -> {
			ServerLevel level = server.overworld();
			Mob pig = spawn(level, EntityTypes.PIG);
			ItemStack treats = new ItemStack(Items.BROWN_MUSHROOM, Animals.treats_needed);
			String last = "";
			for (int i = 0; i < Animals.treats_needed; i++) {
				last = Animals.train(pig, treats);
			}
			boolean trained = pig.entityTags().contains(Animals.truffle_pig);
			pig.discard();
			return trained && treats.isEmpty() ? null : last;
		});
		SelfTest.add("pack harness turns a llama into a pack mule", server -> {
			ServerLevel level = server.overworld();
			Llama llama = spawn(level, EntityTypes.LLAMA);
			llama.setTamed(true);
			String result = Animals.harness(llama, new ItemStack(Content.item("pack_harness")));
			int strength = llama.getStrength();
			llama.discard();
			return strength == 5 ? null : result + " strength " + strength;
		});
		SelfTest.add("lava fishing and honey types", server -> {
			boolean eel = Animals.lava_catch(-40).is(Content.item("magma_eel"));
			boolean fish = Animals.lava_catch(500).is(Content.item("lava_fish"));
			boolean none = Animals.lava_catch(900).isEmpty();
			int glowing = CookData.props(Animals.honey("glow")).getOrDefault("glowing", 0);
			int plain = CookData.props(Animals.honey("")).getOrDefault("glowing", 0);
			return eel && fish && none && glowing == 2 && plain == 0 ? null : eel + " " + fish + " " + none + " glow " + glowing + " plain " + plain;
		});
	}
}
