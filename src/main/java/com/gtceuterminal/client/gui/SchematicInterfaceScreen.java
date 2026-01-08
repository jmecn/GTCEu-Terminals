package com.gtceuterminal.client.gui;

import com.gtceuterminal.common.data.SchematicData;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;

public class SchematicInterfaceScreen extends Screen {

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 240;

    private ItemStack terminalItem;
    private List<SchematicData> schematics;
    private final Consumer<SchematicAction> onAction;

    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private float previewRotationX = 30;
    private float previewRotationY = -45;
    private boolean isDraggingPreview = false;
    private int lastMouseX = 0;
    private int lastMouseY = 0;

    private EditBox nameInput;
    private Button saveButton;
    private Button loadButton;
    private Button deleteButton;
    private Button pasteButton;

    public SchematicInterfaceScreen(ItemStack terminalItem, List<SchematicData> schematics,
                                   Consumer<SchematicAction> onAction) {
        super(Component.literal("schematic Terminal"));
        this.terminalItem = terminalItem;
        this.schematics = schematics;
        this.onAction = onAction;

        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        int totalLines = schematics.size();
        int visibleLines = 8;
        maxScroll = Math.max(0, totalLines - visibleLines);
    }

    @Override
    protected void init() {
        super.init();

        int leftPos = (this.width - GUI_WIDTH) / 2;
        int topPos = (this.height - GUI_HEIGHT) / 2;

        // Name input for new schematics
        nameInput = new EditBox(this.font, leftPos + 10, topPos + 10, 120, 20,
                Component.literal("schematic Name"));
        nameInput.setMaxLength(32);
        nameInput.setValue("");
        nameInput.setHint(Component.literal("Enter name..."));
        this.addRenderableWidget(nameInput);

        // Save current clipboard as new schematic
        saveButton = Button.builder(Component.literal("Save Clipboard"),
                        btn -> {
                            // Validate clipboard exists and has blocks
                            CompoundTag tag = terminalItem.getTag();
                            if (tag == null || !tag.contains("Clipboard")) {
                                net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                                        Component.literal("§cNo clipboard! Copy a multiblock first."),
                                        true
                                );
                                return;
                            }

                            // Check if clipboard actually has blocks
                            CompoundTag clipboardTag = tag.getCompound("Clipboard");
                            if (!clipboardTag.contains("Blocks") || clipboardTag.getList("Blocks", 10).isEmpty()) {
                                net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                                        Component.literal("§cClipboard is empty!"),
                                        true
                                );
                                return;
                            }

                            String name = nameInput.getValue().trim();
                            if (name.isEmpty()) {
                                name = "Unnamed schematic";
                            }

                            final String finalName = name;
                            boolean isDuplicate = schematics.stream()
                                    .anyMatch(bp -> bp.getName().equals(finalName));

                            if (isDuplicate) {
                                net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                                        Component.literal("§cSchematic name already exists!"),
                                        true
                                );
                                return;
                            }

                            com.gtceuterminal.common.network.TerminalNetwork.CHANNEL.sendToServer(
                                    new com.gtceuterminal.common.network.CPacketSchematicAction(
                                            com.gtceuterminal.common.network.CPacketSchematicAction.ActionType.SAVE,
                                            finalName,
                                            -1
                                    )
                            );

                            reloadSchematics();
                            nameInput.setValue("");

                            new java.util.Timer().schedule(new java.util.TimerTask() {
                                @Override
                                public void run() {
                                    net.minecraft.client.Minecraft.getInstance().execute(() -> {
                                        reloadSchematics();
                                    });
                                }
                            }, 50);
                        })
                .bounds(leftPos + 135, topPos + 10, 85, 20)
                .build();
        this.addRenderableWidget(saveButton);

        // Load selected schematic to clipboard
        loadButton = Button.builder(Component.literal("Load"),
                        btn -> {
                            if (selectedIndex >= 0 && selectedIndex < schematics.size()) {
                                com.gtceuterminal.common.network.TerminalNetwork.CHANNEL.sendToServer(
                                        new com.gtceuterminal.common.network.CPacketSchematicAction(
                                                com.gtceuterminal.common.network.CPacketSchematicAction.ActionType.LOAD,
                                                "",
                                                selectedIndex
                                        )
                                );
                            }
                        })
                .bounds(leftPos + 225, topPos + 10, 40, 20)
                .build();
        this.addRenderableWidget(loadButton);

        // Delete selected schematic
        deleteButton = Button.builder(Component.literal("Delete"),
                        btn -> {
                            if (selectedIndex >= 0 && selectedIndex < schematics.size()) {
                                com.gtceuterminal.common.network.TerminalNetwork.CHANNEL.sendToServer(
                                        new com.gtceuterminal.common.network.CPacketSchematicAction(
                                                com.gtceuterminal.common.network.CPacketSchematicAction.ActionType.DELETE,
                                                "",
                                                selectedIndex
                                        )
                                );

                                selectedIndex = -1;
                                reloadSchematics();

                                new java.util.Timer().schedule(new java.util.TimerTask() {
                                    @Override
                                    public void run() {
                                        net.minecraft.client.Minecraft.getInstance().execute(() -> {
                                            reloadSchematics();
                                        });
                                    }
                                }, 50);
                            }
                        })
                .bounds(leftPos + 270, topPos + 10, 40, 20)
                .build();
        this.addRenderableWidget(deleteButton);

        pasteButton = Button.builder(Component.literal("Close"),
                        btn -> this.onClose())
                .bounds(leftPos + 10, topPos + 210, 300, 20)
                .build();
        this.addRenderableWidget(pasteButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int leftPos = (this.width - GUI_WIDTH) / 2;
        int topPos = (this.height - GUI_HEIGHT) / 2;

        // Background
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xC0101010);
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + 1, 0xFF606060);
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + GUI_HEIGHT, 0xFF606060);
        graphics.fill(leftPos + GUI_WIDTH - 1, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFF202020);
        graphics.fill(leftPos, topPos + GUI_HEIGHT - 1, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFF202020);

        // Title
        graphics.drawString(this.font, "Saved schematics", leftPos + 10, topPos + 40, 0xFFFFFFFF);

        // schematic list area
        graphics.fill(leftPos + 10, topPos + 55, leftPos + 155, topPos + 200, 0xFF404040);

        // Render schematic list
        graphics.enableScissor(leftPos + 10, topPos + 55, leftPos + 155, topPos + 200);
        renderSchematicList(graphics, leftPos, topPos, mouseX, mouseY);
        graphics.disableScissor();

        if (maxScroll > 0) {
            int scrollbarHeight = Math.max(20, 145 * 8 / schematics.size());
            int scrollbarY = topPos + 55 + (int)((145 - scrollbarHeight) * ((float)scrollOffset / maxScroll));
            graphics.fill(leftPos + 155, topPos + 55, leftPos + 160, topPos + 200, 0xFF202020);
            graphics.fill(leftPos + 155, scrollbarY, leftPos + 160, scrollbarY + scrollbarHeight, 0xFF808080);
        }

        // Preview area
        graphics.fill(leftPos + 165, topPos + 55, leftPos + 310, topPos + 200, 0xFF303030);
        graphics.drawString(this.font, "Preview (Drag to Rotate)", leftPos + 170, topPos + 60, 0xFFFFAA00);

        if (selectedIndex >= 0 && selectedIndex < schematics.size()) {
            SchematicData schematic = schematics.get(selectedIndex);
            renderPreview(graphics, leftPos + 165, topPos + 75, 145, 120, schematic);

            graphics.drawString(this.font, "Size: " + schematic.getSize().toShortString(),
                    leftPos + 170, topPos + 185, 0xFFCCCCCC);
            graphics.drawString(this.font, "Blocks: " + schematic.getBlockCount(),
                    leftPos + 170, topPos + 195, 0xFFCCCCCC);
        } else {
            graphics.drawString(this.font, "Select a schematic", leftPos + 205, topPos + 130, 0xFF888888);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSchematicList(GuiGraphics graphics, int leftPos, int topPos, int mouseX, int mouseY) {
        int yPos = topPos + 58;
        int index = 0;

        for (int i = scrollOffset; i < schematics.size() && index < 8; i++, index++) {
            SchematicData schematic = schematics.get(i);

            boolean isSelected = (i == selectedIndex);
            boolean isHovered = mouseX >= leftPos + 10 && mouseX <= leftPos + 155 &&
                    mouseY >= yPos && mouseY <= yPos + 18;

            if (isSelected) {
                graphics.fill(leftPos + 12, yPos, leftPos + 153, yPos + 17, 0xFF4040FF);
            } else if (isHovered) {
                graphics.fill(leftPos + 12, yPos, leftPos + 153, yPos + 17, 0xFF404040);
            }

            String displayName = schematic.getName();
            if (displayName.length() > 18) {
                displayName = displayName.substring(0, 18) + "...";
            }

            int color = isSelected ? 0xFFFFFF00 : 0xFFCCCCCC;
            graphics.drawString(this.font, displayName, leftPos + 15, yPos + 4, color);

            yPos += 18;
        }
    }

    private void renderPreview(GuiGraphics graphics, int x, int y, int width, int height, SchematicData schematic) {
        if (schematic == null || schematic.getBlocks().isEmpty()) {
            return;
        }

        BlockPos size = schematic.getSize();
        if (size.getX() == 0 || size.getY() == 0 || size.getZ() == 0) {
            return;
        }

        float maxDimension = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));
        float scale = (Math.min(width, height) / maxDimension) * 0.7f;

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        poseStack.translate(x + width / 2.0f, y + height / 2.0f, 200);
        poseStack.scale(scale, -scale, scale);

        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(previewRotationX));
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(previewRotationY));

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos pos : schematic.getBlocks().keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }

        poseStack.translate(
                -(minX + size.getX() / 2.0f),
                -(minY + size.getY() / 2.0f),
                -(minZ + size.getZ() / 2.0f)
        );

        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        for (var entry : schematic.getBlocks().entrySet()) {
            BlockPos relPos = entry.getKey();
            BlockState state = entry.getValue();

            poseStack.pushPose();
            poseStack.translate(relPos.getX(), relPos.getY(), relPos.getZ());

            try {
                blockRenderer.renderSingleBlock(
                        state,
                        poseStack,
                        buffer,
                        15728880, // full bright
                        OverlayTexture.NO_OVERLAY
                );
            } catch (Exception ignored) {
            }

            poseStack.popPose();
        }

        buffer.endBatch();
        poseStack.popPose();
    }
    private int getBlockColor(String path) {
        if (path.contains("coil")) return 0xFFFF8800;
        if (path.contains("casing")) return 0xFF808080;
        if (path.contains("hatch") || path.contains("bus")) return 0xFF00AAFF;
        if (path.contains("pipe")) return 0xFF666666;
        return 0xFFAAAAAA;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int leftPos = (this.width - GUI_WIDTH) / 2;
        int topPos = (this.height - GUI_HEIGHT) / 2;

        if (mouseX >= leftPos + 10 && mouseX <= leftPos + 155 &&
                mouseY >= topPos + 55 && mouseY <= topPos + 200) {

            int relativeY = (int)(mouseY - topPos - 58);
            int clickedIndex = scrollOffset + (relativeY / 18);

            if (clickedIndex >= 0 && clickedIndex < schematics.size()) {
                selectedIndex = clickedIndex;
                return true;
            }
        }

        if (mouseX >= leftPos + 165 && mouseX <= leftPos + 310 &&
                mouseY >= topPos + 55 && mouseY <= topPos + 200) {
            isDraggingPreview = true;
            lastMouseX = (int)mouseX;
            lastMouseY = (int)mouseY;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingPreview) {
            int deltaX = (int)mouseX - lastMouseX;
            int deltaY = (int)mouseY - lastMouseY;

            previewRotationY += deltaX * 0.5f;
            previewRotationX += deltaY * 0.5f;

            previewRotationX = Math.max(-89, Math.min(89, previewRotationX));

            lastMouseX = (int)mouseX;
            lastMouseY = (int)mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)delta));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void reloadSchematics() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var player = minecraft.player;

        if (player != null) {
            ItemStack updatedStack = null;

            // Check main hand
            if (player.getMainHandItem().getItem() == terminalItem.getItem()) {
                updatedStack = player.getMainHandItem();
            }
            // Check off hand
            else if (player.getOffhandItem().getItem() == terminalItem.getItem()) {
                updatedStack = player.getOffhandItem();
            }

            // Update reference if found
            if (updatedStack != null) {
                this.terminalItem = updatedStack;
            }
        }

        // Reload schematics from NBT
        CompoundTag tag = terminalItem.getTag();
        if (tag == null || !tag.contains("SavedSchematics")) {
            schematics.clear();
            calculateMaxScroll();
            return;
        }

        schematics.clear();
        ListTag savedList = tag.getList("SavedSchematics", 10);

        for (int i = 0; i < savedList.size(); i++) {
            try {
                CompoundTag schematicTag = savedList.getCompound(i);
                schematics.add(SchematicData.fromNBT(schematicTag, minecraft.level.registryAccess()));
            } catch (Exception e) {
            }
        }

        calculateMaxScroll();
    }

    public static class SchematicAction {
        public final ActionType type;
        public final String name;
        public final int index;

        public SchematicAction(ActionType type, String name, int index) {
            this.type = type;
            this.name = name;
            this.index = index;
        }
    }

    public enum ActionType {
        SAVE,
        LOAD,
        DELETE
    }
}
