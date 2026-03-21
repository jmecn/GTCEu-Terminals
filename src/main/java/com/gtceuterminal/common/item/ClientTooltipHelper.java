package com.gtceuterminal.common.item;

import com.gtceuterminal.common.ae2.MENetworkScanner;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Helper class for appending client-side tooltip information.
 * This is separated from the item classes to avoid accidentally calling client-only code on the server.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientTooltipHelper {

    private ClientTooltipHelper() {}

    /**
     * Appends the AE2 ME Network range indicator to the tooltip.
     * Only call this from {@code appendHoverText} — it is always client-side.
     */
    public static void appendAE2RangeTooltip(ItemStack stack, Level level,
                                              List<Component> tooltipComponents) {
        if (!MENetworkScanner.isItemLinked(stack)) return;

        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null || level == null) return;

        if (MENetworkScanner.isItemInRange(stack, level, localPlayer)) {
            tooltipComponents.add(Component.literal("  ● In Range")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltipComponents.add(Component.literal("  ● Out of Range")
                    .withStyle(ChatFormatting.RED));
        }
    }
}