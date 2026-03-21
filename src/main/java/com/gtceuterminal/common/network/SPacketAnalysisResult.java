package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.autocraft.AnalysisResult;

import net.minecraft.network.FriendlyByteBuf;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client: carries an {@link AnalysisResult} so the client can open
 * {@link com.gtceuterminal.client.gui.autocraft.AutocraftConfirmScreen}.
 *
 * The handler MUST NOT reference any client-only class (Screen, Minecraft)
 * at the top level — doing so crashes the dedicated server when the class
 * is loaded. All client code is isolated behind {@link DistExecutor}.
 */
public class SPacketAnalysisResult {

    private final AnalysisResult result;

    public SPacketAnalysisResult(AnalysisResult result) {
        this.result = result;
    }

    // ── Encode / Decode ───────────────────────────────────────────────────────
    public void encode(FriendlyByteBuf buf) {
        result.encode(buf);
    }

    public SPacketAnalysisResult(FriendlyByteBuf buf) {
        this.result = AnalysisResult.decode(buf);
    }

    // ── Handler ───────────────────────────────────────────────────────────────
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // DistExecutor keeps the lambda off the server classloader entirely.
            // The server never touches Screen or Minecraft.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(result));
        });
        ctx.get().setPacketHandled(true);
    }

    @net.minecraftforge.api.distmarker.OnlyIn(Dist.CLIENT)
    private static void handleClient(AnalysisResult result) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.client.gui.screens.Screen current = mc.screen;
            mc.setScreen(new com.gtceuterminal.client.gui.autocraft.AutocraftConfirmScreen(
                    result, current));
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("SPacketAnalysisResult: failed to open confirm screen", e);
        }
    }
}