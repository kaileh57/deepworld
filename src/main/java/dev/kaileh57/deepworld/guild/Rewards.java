package dev.kaileh57.deepworld.guild;

import dev.kaileh57.deepworld.cooking.Meals;
import dev.kaileh57.deepworld.world.ServerState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class Rewards {
	private Rewards() {
	}

	public static void grant(ServerPlayer player, Guild.Milestone milestone) {
		if (milestone.id().equals("gourmet")) {
			ServerState state = ServerState.get(player.level().getServer());
			if (!state.gourmet_awarded) {
				state.gourmet_awarded = true;
				state.bonus_meal_slots++;
				state.setDirty();
				Meals.server_bonus_slots = state.bonus_meal_slots;
				player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal("everyone gains a fourth meal slot"), false);
			}
		}
	}
}
