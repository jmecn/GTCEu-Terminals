package com.gtceuterminal.client.gui.dialog;

import com.gtceuterminal.common.material.ComponentUpgradeHelper;
import com.gtceuterminal.common.material.MaterialAvailability;
import com.gtceuterminal.common.material.MaterialCalculator;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.MultiblockInfo;
import com.gtceuterminal.common.upgrade.ComponentUpgrader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BulkUpgradeDialog extends Screen {

    private final Screen parent;
    private final MultiblockInfo multiblock;
    private final int targetTier;
    private final Player player;
    private final Runnable onSuccess;

    private static final int DIALOG_WIDTH = 300;
    private static final int DIALOG_HEIGHT = 200;

    private int dialogX;
    private int dialogY;

    private Button confirmButton;
    private Button cancelButton;

    private List<MaterialAvailability> totalMaterials;
    private boolean hasEnough;
    private int upgradeableCount;

    public BulkUpgradeDialog(
            Screen parent,
            MultiblockInfo multiblock,
            int targetTier,
            Player player,
            Runnable onSuccess
    ) {
        super(Component.literal("Bulk Upgrade"));
        this.parent = parent;
        this.multiblock = multiblock;
        this.targetTier = targetTier;
        this.player = player;
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        dialogX = (width - DIALOG_WIDTH) / 2;
        dialogY = (height - DIALOG_HEIGHT) / 2;

        Map<Item, Integer> totalRequired = new HashMap<>();
        upgradeableCount = 0;

        for (ComponentInfo component : multiblock.getUpgradeableComponents()) {
            if (!ComponentUpgradeHelper.canUpgrade(component, targetTier)) {
                continue;
            }

            upgradeableCount++;
            Map<Item, Integer> required = ComponentUpgradeHelper.getUpgradeItems(component, targetTier);
            required.forEach((item, count) ->
                    totalRequired.merge(item, count, Integer::sum)
            );
        }

        totalMaterials = MaterialCalculator.checkMaterialsAvailability(
                totalRequired, player, minecraft.level
        );
        hasEnough = MaterialCalculator.hasEnoughMaterials(totalMaterials);

        // Confirm button
        String confirmText = hasEnough ?
                "Upgrade " + upgradeableCount + " Components" :
                "Missing Materials";

        confirmButton = Button.builder(
                        Component.literal(confirmText),
                        btn -> {
                            if (hasEnough) {
                                performBulkUpgrade();
                            }
                        })
                .bounds(dialogX + 20, dialogY + DIALOG_HEIGHT - 35, 130, 20)
                .build();
        confirmButton.active = hasEnough;
        addRenderableWidget(confirmButton);

        // Cancel button
        cancelButton = Button.builder(
                        Component.literal("Cancel"),
                        btn -> minecraft.setScreen(parent))
                .bounds(dialogX + 160, dialogY + DIALOG_HEIGHT - 35, 120, 20)
                .build();
        addRenderableWidget(cancelButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Darken background
        graphics.fill(0, 0, width, height, 0x80000000);

        // Draw dialog background
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + DIALOG_HEIGHT, 0xFF2B2B2B);
        graphics.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + 2, 0xFF3F3F3F);

        // Draw title
        String tierName = com.gregtechceu.gtceu.api.GTValues.VN[targetTier].toUpperCase(java.util.Locale.ROOT);
        String title = "Bulk Upgrade to " + tierName;
        int titleWidth = font.width(title);
        graphics.drawString(font, title, dialogX + (DIALOG_WIDTH - titleWidth) / 2, dialogY + 10, 0xFFFFFF);

        // Draw multiblock info
        int y = dialogY + 30;

        graphics.drawString(font, "§7Multiblock:", dialogX + 20, y, 0xFFFFFF);
        y += 12;
        graphics.drawString(font, "§b" + multiblock.getName(), dialogX + 20, y, 0xFFFFFF);

        y += 18;
        graphics.drawString(font, "§7Components: §f" + upgradeableCount + " upgradeable", dialogX + 20, y, 0xFFFFFF);

        y += 18;
        graphics.drawString(font, "§7Total Materials Required:", dialogX + 20, y, 0xFFFFFF);
        y += 12;

        // Draw materials list
        int maxDisplay = 5;
        int displayed = 0;

        for (MaterialAvailability mat : totalMaterials) {
            if (displayed >= maxDisplay) {
                graphics.drawString(font, "§7... and more", dialogX + 25, y, 0xFFFFFF);
                break;
            }

            String icon = mat.hasEnough() ? "§a✓" : "§c✗";
            String matText = icon + " §7" + mat.getItemName();
            graphics.drawString(font, matText, dialogX + 25, y, 0xFFFFFF);

            String counts = "§7x" + mat.getRequired();
            graphics.drawString(font, counts, dialogX + 220, y, 0xFFFFFF);

            y += 10;
            displayed++;
        }

        y = dialogY + DIALOG_HEIGHT - 55;
        if (!hasEnough) {
            graphics.drawString(font, "§c⚠ Missing materials!", dialogX + 20, y, 0xFFFFFF);
        } else {
            graphics.drawString(font, "§a✓ All materials available", dialogX + 20, y, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void performBulkUpgrade() {
        List<ComponentInfo> components = multiblock.getUpgradeableComponents();

        ComponentUpgrader.BulkUpgradeResult result = ComponentUpgrader.upgradeMultipleComponents(
                components,
                targetTier,
                player,
                minecraft.level
        );

        if (result.success) {
            player.displayClientMessage(
                    Component.literal(String.format(
                            "§a✓ Upgraded %d components! (Failed: %d, Skipped: %d)",
                            result.successful, result.failed, result.skipped
                    )),
                    true
            );
            player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            if (onSuccess != null) {
                onSuccess.run();
            }
        } else {
            player.displayClientMessage(
                    Component.literal("§c✗ " + result.message),
                    true
            );
            player.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
        }

        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}