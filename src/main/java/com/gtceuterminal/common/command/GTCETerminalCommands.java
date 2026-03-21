package com.gtceuterminal.common.command;

import com.gtceuterminal.common.theme.DefaultThemeConfig;
import com.gtceuterminal.common.theme.ItemTheme;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.registries.ForgeRegistries;

/**
 * Registers /gtcet commands.
 *
 * Usage:
 *   /gtcet theme set-default       — saves the admin's current item theme as the server default
 *   /gtcet theme reset-default     — resets default_theme.json to built-in values
 *   /gtcet theme reload            — reloads default_theme.json from disk
 *   /gtcet theme info              — shows current default theme colors
 */
public class GTCETerminalCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("gtcet")
                .then(Commands.literal("theme")
                    .requires(src -> src.hasPermission(2)) // OP level 2
                    .then(Commands.literal("set-default")
                        .executes(GTCETerminalCommands::setDefault))
                    .then(Commands.literal("reset-default")
                        .executes(GTCETerminalCommands::resetDefault))
                    .then(Commands.literal("reload")
                        .executes(GTCETerminalCommands::reloadConfig))
                    .then(Commands.literal("info")
                        .executes(GTCETerminalCommands::showInfo))
                )
        );
    }

    // ─── /gtcet theme set-default ────────────────────────────────────────────
    // Reads the theme from the admin's held terminal item and saves it as default
    private static int setDefault(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("§cMust be run by a player."));
            return 0;
        }

        // Find terminal item in hands
        ItemStack found = findTerminal(player);
        if (found.isEmpty()) {
            src.sendFailure(Component.literal(
                "§cHold a GTCEu Terminal item in your main or offhand."));
            return 0;
        }

        ItemTheme theme = ItemTheme.load(found);
        DefaultThemeConfig.save(theme);
        DefaultThemeConfig.reload();

        src.sendSuccess(() -> Component.literal(
            "§aDefault theme saved! New players will use: " +
            "Accent §r" + colorTag(theme.accentColor) +
            " BG §r" + colorTag(theme.bgColor) +
            " Panel §r" + colorTag(theme.panelColor)), true);
        return 1;
    }

    // ─── /gtcet theme reset-default ──────────────────────────────────────────
    private static int resetDefault(CommandContext<CommandSourceStack> ctx) {
        DefaultThemeConfig.save(new ItemTheme()); // built-in defaults
        DefaultThemeConfig.reload();
        ctx.getSource().sendSuccess(() ->
            Component.literal("§aDefault theme reset to built-in values."), true);
        return 1;
    }

    // ─── /gtcet theme reload ─────────────────────────────────────────────────
    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        DefaultThemeConfig.reload();
        ctx.getSource().sendSuccess(() ->
            Component.literal("§adefault_theme.json reloaded from disk."), true);
        return 1;
    }

    // ─── /gtcet theme info ───────────────────────────────────────────────────
    private static int showInfo(CommandContext<CommandSourceStack> ctx) {
        ItemTheme t = DefaultThemeConfig.get();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§7Default theme: " +
            "§fAccent §r" + colorTag(t.accentColor) +
            " §fBG §r" + colorTag(t.bgColor) +
            " §fPanel §r" + colorTag(t.panelColor) +
            (t.hasWallpaper() ? " §fWallpaper: §e" + t.wallpaper : "")), false);
        return 1;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private static ItemStack findTerminal(ServerPlayer player) {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (isTerminal(s)) return s;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (isTerminal(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isTerminal(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return rl != null && "gtceuterminal".equals(rl.getNamespace());
    }

    private static String colorTag(int argb) {
        return String.format("§f#%06X", argb & 0xFFFFFF);
    }
}