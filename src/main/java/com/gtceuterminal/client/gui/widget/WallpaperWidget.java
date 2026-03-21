package com.gtceuterminal.client.gui.widget;

import com.gtceuterminal.client.gui.theme.WallpaperManager;
import com.gtceuterminal.common.theme.ItemTheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import org.joml.Matrix4f;

import java.util.function.Supplier;

/**
 * Renders a wallpaper image scaled (FIT) and centered, never tiled.
 * UV 0→1 quad via Tesselator so the image is always drawn exactly once.
 * Add as the FIRST widget in mainGroup.
 */
public class WallpaperWidget extends Widget {

    private final Supplier<ItemTheme> themeSupplier;

    public WallpaperWidget(int x, int y, int width, int height, Supplier<ItemTheme> themeSupplier) {
        super(x, y, width, height);
        this.themeSupplier = themeSupplier;
        setClientSideWidget();
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ItemTheme theme = themeSupplier.get();
        Position pos  = getPosition();
        Size     size = getSize();

        int x = pos.x;
        int y = pos.y;
        int w = size.width;
        int h = size.height;

        // Solid BG first
        graphics.fill(x, y, x + w, y + h, theme.bgColor);

        if (!theme.hasWallpaper()) return;

        ResourceLocation rl = WallpaperManager.getTexture(theme.wallpaper);
        if (rl == null) return;

        // Actual image dimensions
        int imgW = w;
        int imgH = h;
        try {
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(rl);
            if (tex instanceof DynamicTexture dt && dt.getPixels() != null) {
                imgW = dt.getPixels().getWidth();
                imgH = dt.getPixels().getHeight();
            }
        } catch (Exception ignored) {}

        // FIT: scale to fill panel while preserving aspect ratio
        float scale = Math.min((float) w / imgW, (float) h / imgH);
        float drawW = imgW * scale;
        float drawH = imgH * scale;
        float drawX = x + (w - drawW) / 2f;
        float drawY = y + (h - drawH) / 2f;

        // Raw quad with UV 0→1 (no tiling)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, rl);

        Matrix4f mat = graphics.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        buf.vertex(mat, drawX,        drawY + drawH, 0f).uv(0f, 1f).endVertex();
        buf.vertex(mat, drawX + drawW, drawY + drawH, 0f).uv(1f, 1f).endVertex();
        buf.vertex(mat, drawX + drawW, drawY,         0f).uv(1f, 0f).endVertex();
        buf.vertex(mat, drawX,         drawY,         0f).uv(0f, 0f).endVertex();

        tess.end();
        RenderSystem.disableBlend();
    }
}