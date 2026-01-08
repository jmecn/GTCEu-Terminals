package com.gtceuterminal.client.gui.multiblock;

import com.gtceuterminal.common.multiblock.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main GUI for managing multiple multiblocks
 */
public class MultiStructureManagerScreen extends Screen {

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 240;
    private static final int SCAN_RADIUS = 32;

    private final Player player;
    private final ItemStack terminalItem;

    private List<MultiblockInfo> multiblocks = new ArrayList<>();
    private List<Boolean> selectedMultiblocks = new ArrayList<>();
    private Map<Integer, Boolean> expandedMultiblocks = new HashMap<>(); // Track which are expanded

    private int scrollOffset = 0;
    private int maxScroll = 0;

    private Button refreshButton;
    private Button highlightAllButton;
    private Button clearHighlightsButton;

    private int hoveredMultiblockIndex = -1;

    private int leftPos;
    private int topPos;

    /**
     * Open the Multi-Structure Manager GUI
     * needs polish, next update
     */
    public static void open(Player player, ItemStack terminalItem) {
        Minecraft.getInstance().setScreen(new MultiStructureManagerScreen(player, terminalItem));
    }

    public MultiStructureManagerScreen(Player player, ItemStack terminalItem) {
        super(Component.literal("Multi-Structure Manager"));
        this.player = player;
        this.terminalItem = terminalItem;
    }

    @Override
    protected void init() {
        super.init();

        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        // Scan for multiblocks
        scanMultiblocks();

        // Refresh button
        refreshButton = Button.builder(
                        Component.literal("↻"),
                        btn -> {
                            scanMultiblocks();
                            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                        }
                )
                .bounds(leftPos + GUI_WIDTH - 30, topPos + 5, 25, 20)
                .build();
        addRenderableWidget(refreshButton);

        // Highlight All button
        highlightAllButton = Button.builder(
                        Component.literal("Highlight All"),
                        btn -> {
                            for (MultiblockInfo mb : multiblocks) {
                                com.gtceuterminal.client.highlight.MultiblockHighlighter.highlightByStatus(mb, 10000);
                            }
                            player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.5f);
                        }
                )
                .bounds(leftPos + 10, topPos + GUI_HEIGHT - 25, 80, 20)
                .build();
        addRenderableWidget(highlightAllButton);

        // Clear Highlights button
        clearHighlightsButton = Button.builder(
                        Component.literal("Clear All"),
                        btn -> {
                            com.gtceuterminal.client.highlight.MultiblockHighlighter.clearAll();
                            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                        }
                )
                .bounds(leftPos + 95, topPos + GUI_HEIGHT - 25, 70, 20)
                .build();
        addRenderableWidget(clearHighlightsButton);

        createMultiblockButtons();

        calculateMaxScroll();
    }

    private void scanMultiblocks() {
        multiblocks = MultiblockScanner.scanNearbyMultiblocks(
                player,
                player.level(),
                SCAN_RADIUS
        );

        selectedMultiblocks = new ArrayList<>();
        for (int i = 0; i < multiblocks.size(); i++) {
            selectedMultiblocks.add(false);
        }

        updateMultiblockStatuses();
    }

    private void updateMultiblockStatuses() {
        for (MultiblockInfo mb : multiblocks) {
            MultiblockStatus status = MultiblockStatusScanner.getStatus(mb.getController());
            mb.setStatus(status);
        }
    }

    private void createMultiblockButtons() {
        clearWidgets();

        addRenderableWidget(refreshButton);
        addRenderableWidget(highlightAllButton);
        addRenderableWidget(clearHighlightsButton);

        int y = topPos + 30;
        int visibleCount = Math.min(4, multiblocks.size() - scrollOffset);

        for (int i = scrollOffset; i < scrollOffset + visibleCount; i++) {
            if (i >= multiblocks.size()) break;

            MultiblockInfo mb = multiblocks.get(i);
            final int index = i;

            // Locate button
            Button locateBtn = Button.builder(
                            Component.literal("👁"),
                            btn -> {
                                com.gtceuterminal.client.highlight.MultiblockHighlighter.highlightByStatus(mb, 10000);
                                player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.5f);
                            }
                    )
                    .bounds(leftPos + GUI_WIDTH - 30, y + 6, 20, 16)
                    .build();
            addRenderableWidget(locateBtn);

            y += 50;
        }
    }

    private void calculateMaxScroll() {
        int totalEntries = multiblocks.size();
        int visibleEntries = 4;
        maxScroll = Math.max(0, totalEntries - visibleEntries);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render background
        renderBackground(graphics);

        // Render GUI background
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xC0101010);

        // Render border
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + 1, 0xFF8B8B8B);
        graphics.fill(leftPos, topPos + GUI_HEIGHT - 1, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFF8B8B8B);
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + GUI_HEIGHT, 0xFF8B8B8B);
        graphics.fill(leftPos + GUI_WIDTH - 1, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFF8B8B8B);

        // Render title
        String title = "Nearby Multiblocks (" + SCAN_RADIUS + " blocks)";
        graphics.drawString(font, title, leftPos + 10, topPos + 10, 0xFFFFFF);

        // Render multiblock list
        renderMultiblockList(graphics, mouseX, mouseY);

        // Render widgets (buttons, checkboxes)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render tooltips
        renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderMultiblockList(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = topPos + 30;
        int visibleCount = Math.min(4, multiblocks.size() - scrollOffset);

        hoveredMultiblockIndex = -1;

        for (int i = scrollOffset; i < scrollOffset + visibleCount; i++) {
            if (i >= multiblocks.size()) break;

            MultiblockInfo mb = multiblocks.get(i);
            boolean isExpanded = expandedMultiblocks.getOrDefault(i, false);

            boolean isHovered = mouseX >= leftPos + 30 && mouseX <= leftPos + GUI_WIDTH - 40 &&
                    mouseY >= y && mouseY <= y + 20;

            if (isHovered) {
                hoveredMultiblockIndex = i;
                graphics.fill(leftPos + 10, y, leftPos + GUI_WIDTH - 10, y + 20, 0x40FFFFFF);
            }

            // Render expand/collapse arrow
            String arrow = isExpanded ? "▼" : "▶";
            graphics.drawString(font, arrow, leftPos + 15, y + 6, 0xFFFFFF);

            // Render multiblock name
            String name = mb.getName();
            graphics.drawString(font, name, leftPos + 30, y + 5, 0xFFFFFF);

            // Render distance
            String distance = mb.getDistanceString();
            graphics.drawString(font, distance, leftPos + 200, y + 5, 0xAAAAAA);

            // Render status indicator
            int statusColor = mb.getStatus().getColor();
            graphics.fill(leftPos + GUI_WIDTH - 50, y + 7, leftPos + GUI_WIDTH - 42, y + 15, statusColor | 0xFF000000);

            y += 22;

            // Render components if expanded
            if (isExpanded) {
                var groups = mb.getGroupedComponents();

                if (groups.isEmpty()) {
                    graphics.drawString(font, "  No upgradeable components", leftPos + 35, y, 0x888888);
                    y += 12;
                } else {
                    for (com.gtceuterminal.common.multiblock.ComponentGroup group : groups) {
                        try {
                            com.gtceuterminal.common.multiblock.ComponentInfo rep = group.getRepresentative();
                            if (rep != null) {
                                net.minecraft.world.level.block.state.BlockState state =
                                        minecraft.level.getBlockState(rep.getPosition());
                                net.minecraft.world.level.block.Block block = state.getBlock();
                                net.minecraft.world.item.ItemStack iconStack = new net.minecraft.world.item.ItemStack(block);

                                if (!iconStack.isEmpty()) {
                                    graphics.renderItem(iconStack, leftPos + 37, y - 1);
                                }
                            }
                        } catch (Exception e) {
                        }

                        // Component name, tier, and count
                        ComponentInfo rep = group.getRepresentative();
                        String compName = rep != null ? rep.getDisplayName() : group.getType().getDisplayName();
                        String compTier = group.getTierName();
                        int count = group.getCount();

                        boolean showTier = group.getType() != com.gtceuterminal.common.multiblock.ComponentType.COIL
                                && group.getType() != com.gtceuterminal.common.multiblock.ComponentType.MAINTENANCE;

                        String displayText = compName;
                        if (showTier) {
                            displayText += " (" + compTier + ")";
                        }
                        if (count > 1) {
                            displayText += " §7x" + count;
                        }

                        graphics.drawString(font, displayText,
                                leftPos + 55, y, 0xCCCCCC);

                        int btnX = leftPos + GUI_WIDTH - 90; // 90 pixels from right edge
                        graphics.drawString(font, "[Upgrade...]", btnX, y, 0xAADDFF);

                        y += 12;
                    }
                }

                y += 5;
            }
        }

        // Render scroll indicator
        if (maxScroll > 0) {
            String scrollText = "(" + (scrollOffset + 1) + "-" +
                    Math.min(scrollOffset + 4, multiblocks.size()) +
                    " of " + multiblocks.size() + ")";
            graphics.drawString(font, scrollText, leftPos + GUI_WIDTH - 100, topPos + 10, 0x888888);
        }

        // Render "No multiblocks found" message
        if (multiblocks.isEmpty()) {
            String msg = "No multiblocks found within " + SCAN_RADIUS + " blocks";
            int msgWidth = font.width(msg);
            graphics.drawString(font, msg, leftPos + (GUI_WIDTH - msgWidth) / 2, topPos + 100, 0xFF8888);
        }
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        // Tooltip for hovered multiblock
        if (hoveredMultiblockIndex >= 0 && hoveredMultiblockIndex < multiblocks.size()) {
            MultiblockInfo mb = multiblocks.get(hoveredMultiblockIndex);

            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§b" + mb.getName()));
            tooltip.add(Component.literal("§7Tier: §f" + mb.getTierName()));
            tooltip.add(Component.literal("§7Distance: §f" + mb.getDistanceString()));
            tooltip.add(Component.literal("§7Status: §f" + mb.getStatus().getDisplayName()));

            if (!mb.isFormed()) {
                tooltip.add(Component.literal("§c⚠ Structure not formed!"));
            }

            tooltip.add(Component.empty());
            tooltip.add(Component.literal("§7Components:"));

            for (ComponentType type : ComponentType.values()) {
                int count = mb.countComponentsOfType(type);
                if (count > 0 && type.isUpgradeable()) {
                    tooltip.add(Component.literal("§7  " + type.getIcon() + " " + type.getDisplayName() + ": §f" + count));
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (scrollDelta > 0) {
            // Scroll up
            scrollOffset = Math.max(0, scrollOffset - 1);
            createMultiblockButtons();
            return true;
        } else if (scrollDelta < 0) {
            // Scroll down
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
            createMultiblockButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int y = topPos + 30;

            for (int i = scrollOffset; i < Math.min(scrollOffset + 4, multiblocks.size()); i++) {
                MultiblockInfo mb = multiblocks.get(i);
                boolean isExpanded = expandedMultiblocks.getOrDefault(i, false);

                if (mouseX >= leftPos + 10 && mouseX <= leftPos + GUI_WIDTH - 40 &&
                        mouseY >= y && mouseY <= y + 22) {

                    if (!isExpanded) {
                        expandedMultiblocks.clear();
                    }

                    expandedMultiblocks.put(i, !isExpanded);

                    player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);

                    return true;
                }

                y += 22;

                // Check for upgrade button clicks if expanded
                if (isExpanded) {
                    var groups = mb.getGroupedComponents();

                    for (com.gtceuterminal.common.multiblock.ComponentGroup group : groups) {
                        // Check [Upgrade...]
                        int btnX = leftPos + GUI_WIDTH - 90;

                        if (mouseX >= btnX && mouseX <= btnX + 70 &&
                                mouseY >= y && mouseY <= y + 9) {

                            // Open tier selection dialog
                            openTierSelectionDialog(group, mb);
                            return true;
                        }

                        y += 12;
                    }

                    y += 5;
                } else {
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleUpgradeClick(
            com.gtceuterminal.common.multiblock.ComponentInfo component,
            int targetTier,
            MultiblockInfo multiblock
    ) {
        // Show confirmation dialog
        minecraft.setScreen(new com.gtceuterminal.client.gui.dialog.UpgradeConfirmationDialog(
                this,
                component,
                targetTier,
                multiblock,
                player,
                this::scanMultiblocks
        ));
    }

    private void handleGroupUpgradeClick(
            com.gtceuterminal.common.multiblock.ComponentGroup group,
            int targetTier,
            MultiblockInfo multiblock
    ) {
        // Show group upgrade dialog
        minecraft.setScreen(new com.gtceuterminal.client.gui.dialog.GroupUpgradeDialog(
                this,
                group,
                targetTier,
                multiblock,
                player,
                this::scanMultiblocks
        ));
    }

    private void openTierSelectionDialog(
            com.gtceuterminal.common.multiblock.ComponentGroup group,
            MultiblockInfo multiblock
    ) {
        // Open tier selection dialog
        minecraft.setScreen(new com.gtceuterminal.client.gui.dialog.TierSelectionDialog(
                this,
                group,
                multiblock,
                player,
                this::scanMultiblocks
        ));
    }

    private void handleBulkUpgradeClick(MultiblockInfo multiblock, int targetTier) {
        // Show bulk upgrade dialog
        minecraft.setScreen(new com.gtceuterminal.client.gui.dialog.BulkUpgradeDialog(
                this,
                multiblock,
                targetTier,
                player,
                this::scanMultiblocks
        ));
    }

    @Override
    public void tick() {
        super.tick();

        if (minecraft.level.getGameTime() % 20 == 0) {
            updateMultiblockStatuses();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
