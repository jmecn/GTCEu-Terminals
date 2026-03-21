package com.gtceuterminal.common.network;

import com.gtceuterminal.common.theme.ItemTheme;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * Client → Server: the player saved a theme from the Theme Editor UI.
 *
 * The server re-validates the theme data and saves it to the item tag of the
 * terminal in player's inventory. The client is expected to update its local
 * theme data from the item tag after saving, so the server doesn't send any
 * response back for this action.
 */
public class CPacketSaveTheme {

    private final CompoundTag themeTag;

    public CPacketSaveTheme(ItemTheme theme) {
        this.themeTag = theme.toNBT();
    }

    public CPacketSaveTheme(FriendlyByteBuf buf) {
        this.themeTag = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(themeTag);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || themeTag == null) return;
            ItemStack stack = findTerminalItem(player);
            if (stack == null || stack.isEmpty()) return;
            ItemTheme.save(stack, ItemTheme.fromNBT(themeTag));
            player.getInventory().setChanged();
        });
        ctx.get().setPacketHandled(true);
    }

    private static ItemStack findTerminalItem(ServerPlayer player) {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (isTerminalItem(s)) return s;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (isTerminalItem(s)) return s;
        }
        return null;
    }

    private static boolean isTerminalItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return rl != null && "gtceuterminal".equals(rl.getNamespace());
    }
}