package com.gtceuterminal.client.gui.factory;

import com.gtceuterminal.GTCEUTerminalMod;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Factory for SchematicInterfaceItem UI.
 *
 * Serializes the full ItemStack NBT (schematic list + clipboard) server-side so
 * the client always has the correct data regardless of item-sync timing.
 * This avoids the empty-UI initial-data mismatch that occurs when using
 * HeldItemUIFactory with stateful widgets (TextFieldWidget, LabelWidget, etc.).
 * Must be registered in FMLCommonSetupEvent (immune to KubeJS double-constructor issue).
 */
public class SchematicItemUIFactory extends UIFactory<SchematicItemUIFactory.Holder> {

    public static final ResourceLocation UI_ID = ResourceLocation.fromNamespaceAndPath(
            GTCEUTerminalMod.MOD_ID, "schematic_interface_ui"
    );

    public static final SchematicItemUIFactory INSTANCE = new SchematicItemUIFactory();

    private SchematicItemUIFactory() {
        super(UI_ID);
    }

    // ── Server entry point ────────────────────────────────────────────────────
    public void openUI(ServerPlayer player, ItemStack item) {
        super.openUI(new Holder(false, item), player);
    }

    // ── UIFactory impl ────────────────────────────────────────────────────────
    @Override
    protected ModularUI createUITemplate(Holder holder, Player entityPlayer) {
        holder.attach(entityPlayer);

        try {
            Class<?> uiClass = Class.forName(
                    "com.gtceuterminal.client.gui.multiblock.SchematicInterfaceUI");
            var m = uiClass.getMethod("create", Holder.class, Player.class);
            return (ModularUI) m.invoke(null, holder, entityPlayer);
        } catch (Throwable t) {
            GTCEUTerminalMod.LOGGER.error("Failed to create Schematic Interface UI", t);
            return null;
        }
    }

    @Override
    protected Holder readHolderFromSyncData(FriendlyByteBuf buf) {
        ItemStack item = buf.readItem();
        return new Holder(true, item);
    }

    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf buf, Holder holder) {
        buf.writeItem(holder.item);
    }

    // ── Holder ────────────────────────────────────────────────────────────────
    public static class Holder implements IUIHolder {
        public final boolean remote;
        public final ItemStack item;
        private Player player;

        public Holder(boolean remote, ItemStack item) {
            this.remote = remote;
            this.item   = item;
        }

        public void attach(Player p) {
            if (this.player == null) this.player = p;
        }

        public Player getPlayer()       { return player; }
        public ItemStack getTerminalItem() { return item; }

        @Override public ModularUI createUI(Player p) { attach(p); return INSTANCE.createUITemplate(this, p); }
        @Override public boolean isInvalid()  { return player != null && player.isRemoved(); }
        @Override public boolean isRemote()   { return remote; }
        @Override public void markAsDirty()   { }
    }
}