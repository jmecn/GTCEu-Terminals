package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.autocraft.AnalysisResult;
import com.gtceuterminal.common.autocraft.MultiblockAnalyzer;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
    * Client → Server: request an analysis for a potential upgrade.
    *
    * The client sends the current components in the group, and the server re-runs
    * the analysis to determine if the upgrade is valid and what the new component
    * tiers would be. The server then responds with SPacketAnalysisResult.
    */
public class CPacketRequestUpgradeAnalysis {

    // One entry per component in the group
    private static final class ComponentEntry {
        final BlockPos     pos;
        final ComponentType type;
        final int           tier;

        ComponentEntry(BlockPos pos, ComponentType type, int tier) {
            this.pos  = pos;
            this.type = type;
            this.tier = tier;
        }
    }

    private final List<ComponentEntry> components;
    private final int                  targetTier;
    private final String               upgradeId;
    private final BlockPos             controllerPos;

    // Called from {@link com.gtceuterminal.client.gui.dialog.ComponentUpgradeDialog}.
    public CPacketRequestUpgradeAnalysis(List<ComponentInfo> infos,
                                          int targetTier,
                                          String upgradeId,
                                          BlockPos controllerPos) {
        this.components    = new ArrayList<>(infos.size());
        for (ComponentInfo ci : infos)
            this.components.add(new ComponentEntry(ci.getPosition(), ci.getType(), ci.getTier()));
        this.targetTier    = targetTier;
        this.upgradeId     = upgradeId != null ? upgradeId : "";
        this.controllerPos = controllerPos;
    }

    // ── Encode / Decode ───────────────────────────────────────────────────────
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(controllerPos);
        buf.writeInt(targetTier);
        buf.writeUtf(upgradeId);
        buf.writeInt(components.size());
        for (ComponentEntry e : components) {
            buf.writeBlockPos(e.pos);
            buf.writeEnum(e.type);
            buf.writeInt(e.tier);
        }
    }

    public CPacketRequestUpgradeAnalysis(FriendlyByteBuf buf) {
        this.controllerPos = buf.readBlockPos();
        this.targetTier    = buf.readInt();
        this.upgradeId     = buf.readUtf();
        int count = buf.readInt();
        this.components = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos      pos  = buf.readBlockPos();
            ComponentType type = buf.readEnum(ComponentType.class);
            int           tier = buf.readInt();
            components.add(new ComponentEntry(pos, type, tier));
        }
    }

    // ── Handler ───────────────────────────────────────────────────────────────
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            try {
                // Reconstruct lightweight ComponentInfo from sent data
                List<ComponentInfo> infos = new ArrayList<>(components.size());
                for (ComponentEntry e : components) {
                    BlockState state = player.serverLevel().getBlockState(e.pos);
                    infos.add(new ComponentInfo(e.type, e.tier, e.pos, state));
                }

                if (infos.isEmpty()) {
                    GTCEUTerminalMod.LOGGER.warn("CPacketRequestUpgradeAnalysis: no valid components");
                    return;
                }

                String uid = upgradeId.isEmpty() ? null : upgradeId;
                AnalysisResult result = MultiblockAnalyzer.analyzeForUpgrade(
                        player, infos, targetTier, uid, controllerPos);

                if (result == null) {
                    GTCEUTerminalMod.LOGGER.warn("CPacketRequestUpgradeAnalysis: analysis returned null");
                    return;
                }

                TerminalNetwork.sendToPlayer(new SPacketAnalysisResult(result), player);

            } catch (Exception e) {
                GTCEUTerminalMod.LOGGER.error("CPacketRequestUpgradeAnalysis: error", e);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}