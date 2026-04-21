package com.alchemy.visualclaiming.map.layers;

import codechicken.lib.gui.GuiDraw;
import com.alchemy.visualclaiming.database.FTBChunkClaimPosition;
import com.alchemy.visualclaiming.database.VCClientCache;
import hellfall.visualores.map.DrawUtils;
import hellfall.visualores.map.layers.RenderLayer;
import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;


@Mod.EventBusSubscriber()
public class ChunkClaimingRenderLayer extends RenderLayer {
    public List<FTBChunkClaimPosition> visibleChunks = new ArrayList<>();
    private Long2ShortOpenHashMap claimedMap = new Long2ShortOpenHashMap();
    private FTBChunkClaimPosition hoveredChunk;
    public ChunkClaimingRenderLayer(String key) {
        super(key);
    }

    @Override
    public void render(double cameraX, double cameraZ, double scale) {
        for (FTBChunkClaimPosition chunk : visibleChunks) {
            int teamColor = (chunk.teamColor & 0x00FFFFFF) + 0x77000000;
            DrawUtils.drawOverlayBox(chunk.x, chunk.z, teamColor, teamColor);
            if (chunk.flags == 1) {
                drawTileableDiagonals(chunk.x, chunk.z, 0xFFFF0000);
            }
            drawEdge(chunk.x, chunk.z,
                    !isSameTeam(chunk.x,     chunk.z - 1, chunk.uid),
                    !isSameTeam(chunk.x,     chunk.z + 1, chunk.uid),
                    !isSameTeam(chunk.x - 1, chunk.z,     chunk.uid),
                    !isSameTeam(chunk.x + 1, chunk.z,     chunk.uid),
                    (chunk.teamColor));
        }
    }

    @Override
    public void updateVisibleArea(int dimensionID, int[] visibleBounds) {
        visibleChunks = VCClientCache.instance.getChunkClaimsInArea(dimensionID, visibleBounds);
        claimedMap = new Long2ShortOpenHashMap(visibleChunks.size());
        claimedMap.defaultReturnValue((short) -1);
        for (FTBChunkClaimPosition chunk : visibleChunks) {
            claimedMap.put(pack(chunk.x, chunk.z), chunk.uid);
        }
    }




    @Override
    public void updateHovered(double mouseX, double mouseY, double cameraX, double cameraZ, double scale) {
        ChunkPos mousePos = new ChunkPos(DrawUtils.getMouseBlockPos(mouseX, mouseY, cameraX, cameraZ, scale));
        hoveredChunk = null;
        for (FTBChunkClaimPosition chunkClaimPosition : visibleChunks) {
            if (chunkClaimPosition.x == mousePos.x && chunkClaimPosition.z == mousePos.z) {
                hoveredChunk = chunkClaimPosition;
                break;
            }
        }
    }

    @Override
    public List<String> getTooltip() {
        if (hoveredChunk != null) {
            return hoveredChunk.tooltips;
        }
        return null;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static void drawEdge(int cx, int cz, boolean north, boolean south, boolean west, boolean east, int color) {
        int x0 = cx * 16;
        int z0 = cz * 16;
        int x1 = x0 + 16;
        int z1 = z0 + 16;

        if (north) GuiDraw.drawGradientRectDirect(x0, z0,     x1,     z0 + 1, color, color);
        if (south) GuiDraw.drawGradientRectDirect(x0, z1, x1,     z1 + 1,     color, color);
        if (west)  GuiDraw.drawGradientRectDirect(x0, z0,     x0 + 1, z1 + 1,     color, color);
        if (east)  GuiDraw.drawGradientRectDirect(x1, z0, x1 + 1,     z1 + 1,     color, color);
    }

    private boolean isSameTeam(int x, int z, short uid) {
        return claimedMap.get(pack(x, z)) == uid;
    }


    public static void drawTileableDiagonals(int chunkX, int chunkY, int color) {
        int x = chunkX * 16;
        int y = chunkY * 16;
        int thickness = 1;
        int lineCount = 4;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        float[] colors = DrawUtils.floats(color);
        GlStateManager.color(colors[0], colors[1], colors[2], colors[3]);

        bufferbuilder.begin(7, DefaultVertexFormats.POSITION);

        for (int line = 0; line < lineCount; line++) {
            int offset = line * (16 / lineCount);

            for (int i = 0; i < 16; i++) {
                // wrap the pixel position within the 16x16 tile
                double px = x + i;
                double py = y + ((i + offset) % 16);

                bufferbuilder.pos(px,             py + thickness, 0.0D).endVertex();
                bufferbuilder.pos(px + thickness, py + thickness, 0.0D).endVertex();
                bufferbuilder.pos(px + thickness, py,             0.0D).endVertex();
                bufferbuilder.pos(px,             py,             0.0D).endVertex();
            }
        }

        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

}
