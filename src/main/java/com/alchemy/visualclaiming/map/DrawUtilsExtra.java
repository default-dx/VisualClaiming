package com.alchemy.visualclaiming.map;

import codechicken.lib.gui.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class DrawUtilsExtra {
    /**
     * Renders a 16x16 texture over a chunk in the map view, using GL_REPEAT for seamless tiling.
     * The texture is rendered without any color tinting (pure white color multiplier).
     * Supports animated textures via .mcmeta files placed alongside the PNG.
     *
     * @param chunkX  chunk X coordinate (will be multiplied by 16 to get block coords)
     * @param chunkY  chunk Z coordinate (will be multiplied by 16 to get block coords)
     * @param texture ResourceLocation pointing to the texture to render
     */
    public static void drawTextureTiled(int chunkX, int chunkY, ResourceLocation texture) {
        double x = chunkX * 16;
        double y = chunkY * 16;

        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);

        // GL_REPEAT makes the texture tile seamlessly across chunk boundaries
        // GL_NEAREST keeps pixel art sharp with no interpolation blur
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        // standard alpha blending so transparent PNG pixels are respected
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        GlStateManager.color(1f, 1f, 1f, 1f); // no tint, render texture as-is

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX);

        // quad covers exactly one chunk (16x16 block coords)
        // UV (0,0) to (1,1) maps the full texture once per chunk
        bufferbuilder.pos(x,      y + 16, 0.0D).tex(0, 1).endVertex();
        bufferbuilder.pos(x + 16, y + 16, 0.0D).tex(1, 1).endVertex();
        bufferbuilder.pos(x + 16, y,      0.0D).tex(1, 0).endVertex();
        bufferbuilder.pos(x,      y,      0.0D).tex(0, 0).endVertex();

        tessellator.draw();
        GlStateManager.disableBlend();
    }


    /**
     * Renders a 1px border around the edges of a chunk in the map view.
     * Each edge (north, south, west, east) can be toggled independently.
     *
     * @param cx    chunk X coordinate (will be multiplied by 16 to get block coords)
     * @param cz    chunk Z coordinate (will be multiplied by 16 to get block coords)
     * @param north whether to draw the top edge    (z-)
     * @param south whether to draw the bottom edge (z+)
     * @param west  whether to draw the left edge   (x-)
     * @param east  whether to draw the right edge  (x+)
     * @param color ARGB color of the border
     */
    public static void drawChunkEdge(int cx, int cz, boolean north, boolean south, boolean west, boolean east, int color) {
        int x0 = cx * 16;
        int z0 = cz * 16;
        int x1 = x0 + 16;
        int z1 = z0 + 16;

        if (north) GuiDraw.drawGradientRectDirect(x0, z0,     x1,     z0 + 1, color, color); // top edge,    1px tall
        if (south) GuiDraw.drawGradientRectDirect(x0, z1,     x1,     z1 + 1, color, color); // bottom edge, 1px tall
        if (west)  GuiDraw.drawGradientRectDirect(x0, z0,     x0 + 1, z1 + 1, color, color); // left edge,   1px wide
        if (east)  GuiDraw.drawGradientRectDirect(x1, z0,     x1 + 1, z1 + 1, color, color); // right edge,  1px wide
    }
}
