package com.gtceuterminal.mixins;

import com.gtceuterminal.client.gui.planner.PlannerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MouseHandler.class, remap = false)
public abstract class MixinMouseHandler {

    @Shadow @Final
    private Minecraft minecraft;

    @Redirect(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;turn(DD)V",
                    remap = false
            ),
            remap = false
    )
    private void gtceuterminal$redirectTurn(Entity entity, double yaw, double pitch) {
        if (PlannerState.freeCamActive && PlannerState.cameraEntity != null && minecraft.player != null) {
            if (entity == minecraft.player) {
                PlannerState.cameraEntity.turn(yaw, pitch);
                return;
            }
        }

        entity.turn(yaw, pitch);
    }
}