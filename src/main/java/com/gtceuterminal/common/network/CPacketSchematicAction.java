package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.data.SchematicData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CPacketSchematicAction {

    private final ActionType actionType;
    private final String schematicName;
    private final int schematicIndex;

    public CPacketSchematicAction(ActionType actionType, String schematicName, int schematicIndex) {
        this.actionType = actionType;
        this.schematicName = schematicName;
        this.schematicIndex = schematicIndex;
    }

    public CPacketSchematicAction(FriendlyByteBuf buf) {
        this.actionType = buf.readEnum(ActionType.class);
        this.schematicName = buf.readUtf();
        this.schematicIndex = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(actionType);
        buf.writeUtf(schematicName);
        buf.writeVarInt(schematicIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack stack = findSchematicInterface(player);
            if (stack.isEmpty()) return;

            CompoundTag stackTag = stack.getOrCreateTag();

            switch (actionType) {
                case SAVE -> {
                    if (!stackTag.contains("Clipboard")) {
                        player.displayClientMessage(Component.literal("§eNo clipboard to save!"), true);
                        return;
                    }

                    // Check if clipboard has blocks
                    CompoundTag clipboardTag = stackTag.getCompound("Clipboard");
                    if (!clipboardTag.contains("Blocks") || clipboardTag.getList("Blocks", 10).isEmpty()) {
                        player.displayClientMessage(Component.literal("§eClipboard is empty!"), true);
                        return;
                    }

                    SchematicData clipboard = SchematicData.fromNBT(
                            clipboardTag,
                            player.level().registryAccess()
                    );

                    // Check for duplicate names
                    List<SchematicData> schematics = loadSchematics(stack, player);
                    boolean isDuplicate = schematics.stream()
                            .anyMatch(bp -> bp.getName().equals(schematicName));

                    if (isDuplicate) {
                        player.displayClientMessage(
                                Component.literal("§cSchematic name already exists!"),
                                true
                        );
                        return;
                    }

                    SchematicData named = new SchematicData(
                            schematicName,
                            clipboard.getMultiblockType(),
                            clipboard.getBlocks()
                    );

                    schematics.add(named);
                    saveSchematics(stack, schematics);

                    player.displayClientMessage(
                            Component.literal("§aSaved schematic: " + schematicName),
                            true
                    );
                }

                case LOAD -> {
                    List<SchematicData> schematics = loadSchematics(stack, player);
                    if (schematicIndex >= 0 && schematicIndex < schematics.size()) {
                        SchematicData schematic = schematics.get(schematicIndex);
                        stackTag.put("Clipboard", schematic.toNBT());

                        player.displayClientMessage(
                                Component.literal("§aLoaded schematic: " + schematic.getName()),
                                true
                        );
                    }
                }

                case DELETE -> {
                    List<SchematicData> schematics = loadSchematics(stack, player);
                    if (schematicIndex >= 0 && schematicIndex < schematics.size()) {
                        SchematicData removed = schematics.remove(schematicIndex);
                        saveSchematics(stack, schematics);

                        player.displayClientMessage(
                                Component.literal("§eDeleted schematic: " + removed.getName()),
                                true
                        );
                    }
                }
            }
        });

        ctx.get().setPacketHandled(true);
    }

    private ItemStack findSchematicInterface(ServerPlayer player) {
        // Check main hand
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem().toString().contains("schematic_interface")) {
            return mainHand;
        }

        // Check off hand
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem().toString().contains("schematic_interface")) {
            return offHand;
        }

        return ItemStack.EMPTY;
    }

    private List<SchematicData> loadSchematics(ItemStack stack, ServerPlayer player) {
        List<SchematicData> schematics = new ArrayList<>();

        CompoundTag stackTag = stack.getTag();
        if (stackTag == null || !stackTag.contains("SavedSchematics")) {
            return schematics;
        }

        ListTag savedList = stackTag.getList("SavedSchematics", 10);
        for (int i = 0; i < savedList.size(); i++) {
            try {
                CompoundTag schematicTag = savedList.getCompound(i);
                schematics.add(SchematicData.fromNBT(schematicTag, player.level().registryAccess()));
            } catch (Exception e) {
                GTCEUTerminalMod.LOGGER.error("Error loading schematic {}: {}", i, e.getMessage());
            }
        }

        return schematics;
    }

    private void saveSchematics(ItemStack stack, List<SchematicData> schematics) {
        CompoundTag stackTag = stack.getOrCreateTag();

        ListTag savedList = new ListTag();
        for (SchematicData schematic : schematics) {
            savedList.add(schematic.toNBT());
        }

        stackTag.put("SavedSchematics", savedList);
    }

    public enum ActionType {
        SAVE,
        LOAD,
        DELETE
    }
}
