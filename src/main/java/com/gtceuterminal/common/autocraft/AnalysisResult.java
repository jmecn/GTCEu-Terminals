package com.gtceuterminal.common.autocraft;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a multiblock or upgrade analysis pass.
 *
 * Contains one {@link Entry} per distinct item required, with how many
 * are needed and how many the ME network currently has in stock.
 * The "crafteable" flag means AE2 has a pattern to produce it.
 * Serialised over the network via {@link #encode}/{@link #decode}.
 */
public final class AnalysisResult {

    public static final class Entry {
        // The item needed
        public final ItemStack stack;   // count = amount needed
        // How many the ME network has in stock right now
        public final long inME;
        // Whether AE2 has a crafting pattern for this item
        public final boolean craftable;

        public Entry(ItemStack stack, long inME, boolean craftable) {
            this.stack     = stack;
            this.inME      = inME;
            this.craftable = craftable;
        }

        public int needed()     { return stack.getCount(); }
        public boolean hasAll() { return inME >= needed(); }

        // ── Serialisation ─────────────────────────────────────────────────────
        public void encode(FriendlyByteBuf buf) {
            buf.writeItem(stack);
            buf.writeLong(inME);
            buf.writeBoolean(craftable);
        }

        public static Entry decode(FriendlyByteBuf buf) {
            ItemStack stack    = buf.readItem();
            long      inME     = buf.readLong();
            boolean   craftable= buf.readBoolean();
            return new Entry(stack, inME, craftable);
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────
    // Type of analysis — determines which confirm action the server executes
    public enum Kind { BUILD, UPGRADE }

    public final Kind        kind;
    public final List<Entry> entries;

    /**
     * For BUILD: the controller block position.
     * For UPGRADE: the controller block position (used to look up the multiblock).
     */
    public final net.minecraft.core.BlockPos controllerPos;

    /**
     * For UPGRADE: the target tier (-1 if using upgradeId).
     * For BUILD: unused (0).
     */
    public final int targetTier;

    /**
     * For UPGRADE: the explicit block-id upgrade target (may be null/empty).
     * For BUILD: unused (null).
     */
    public final String upgradeId;

    /**
     * For UPGRADE: the list of component positions to upgrade.
     * For BUILD: empty.
     */
    public final List<net.minecraft.core.BlockPos> componentPositions;

    // ── Constructors ──────────────────────────────────────────────────────────
    public AnalysisResult(List<Entry> entries, net.minecraft.core.BlockPos controllerPos) {
        this.kind               = Kind.BUILD;
        this.entries            = entries;
        this.controllerPos      = controllerPos;
        this.targetTier         = 0;
        this.upgradeId          = null;
        this.componentPositions = List.of();
    }

    public AnalysisResult(List<Entry> entries,
                          net.minecraft.core.BlockPos controllerPos,
                          int targetTier,
                          String upgradeId,
                          List<net.minecraft.core.BlockPos> componentPositions) {
        this.kind               = Kind.UPGRADE;
        this.entries            = entries;
        this.controllerPos      = controllerPos;
        this.targetTier         = targetTier;
        this.upgradeId          = upgradeId;
        this.componentPositions = componentPositions;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    public boolean allAvailable() {
        return entries.stream().allMatch(Entry::hasAll);
    }

    public int missingCount() {
        return (int) entries.stream().filter(e -> !e.hasAll()).count();
    }

    // ── Serialisation ─────────────────────────────────────────────────────────
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeBlockPos(controllerPos);
        buf.writeInt(targetTier);
        buf.writeUtf(upgradeId != null ? upgradeId : "");

        buf.writeInt(componentPositions.size());
        for (var pos : componentPositions) buf.writeBlockPos(pos);

        buf.writeInt(entries.size());
        for (Entry e : entries) e.encode(buf);
    }

    public static AnalysisResult decode(FriendlyByteBuf buf) {
        Kind   kind          = buf.readEnum(Kind.class);
        var    controllerPos = buf.readBlockPos();
        int    targetTier    = buf.readInt();
        String upgradeId     = buf.readUtf();
        if (upgradeId.isEmpty()) upgradeId = null;

        int compCount = buf.readInt();
        List<net.minecraft.core.BlockPos> comps = new ArrayList<>(compCount);
        for (int i = 0; i < compCount; i++) comps.add(buf.readBlockPos());

        int entryCount = buf.readInt();
        List<Entry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) entries.add(Entry.decode(buf));

        if (kind == Kind.BUILD) {
            return new AnalysisResult(entries, controllerPos);
        } else {
            return new AnalysisResult(entries, controllerPos, targetTier, upgradeId, comps);
        }
    }
}