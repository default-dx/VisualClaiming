package com.alchemy.visualclaiming.map.layers;

import com.alchemy.visualclaiming.database.FTBChunkClaimPosition;
import com.alchemy.visualclaiming.database.VCClientCache;
import com.alchemy.visualclaiming.map.DrawUtilsExtra;
import hellfall.visualores.map.DrawUtils;
import hellfall.visualores.map.layers.RenderLayer;
import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;

public class ChunkViewingRenderLayer extends RenderLayer {

    public static ChunkViewingRenderLayer instance;

    private List<FTBChunkClaimPosition> visibleChunks = new ArrayList<>();
    private Long2ShortOpenHashMap claimedMap = new Long2ShortOpenHashMap();
    private FTBChunkClaimPosition hoveredChunk;
    private int lastDimensionID;
    private int[] lastVisibleBounds;
    private boolean dirty = false;

    public ChunkViewingRenderLayer(String key) {
        super(key);
        instance = this;
    }

    @Override
    public void render(double cameraX, double cameraZ, double scale) {
        for (FTBChunkClaimPosition chunk : visibleChunks) {
            int teamColor = (chunk.teamColor & 0x00FFFFFF) + 0x77000000;
            DrawUtils.drawOverlayBox(chunk.x, chunk.z, teamColor, teamColor);
            if (chunk.flags == 1) {
                DrawUtilsExtra.drawTextureTiled(chunk.x, chunk.z, new ResourceLocation("visualclaiming", "textures/tile/loadedchunk.png"));
            }
            DrawUtilsExtra.drawChunkEdge(chunk.x, chunk.z,
                    !isSameTeam(chunk.x,     chunk.z - 1, chunk.uid),
                    !isSameTeam(chunk.x,     chunk.z + 1, chunk.uid),
                    !isSameTeam(chunk.x - 1, chunk.z,     chunk.uid),
                    !isSameTeam(chunk.x + 1, chunk.z,     chunk.uid),
                    chunk.teamColor);
        }
    }

    @Override
    public void updateVisibleArea(int dimensionID, int[] visibleBounds) {
        lastDimensionID = dimensionID;
        lastVisibleBounds = visibleBounds;
        refresh();
    }

    @Override
    public void updateHovered(double mouseX, double mouseY, double cameraX, double cameraZ, double scale) {
        ChunkPos mousePos = new ChunkPos(DrawUtils.getMouseBlockPos(mouseX, mouseY, cameraX, cameraZ, scale));
        hoveredChunk = null;
        for (FTBChunkClaimPosition chunk : visibleChunks) {
            if (chunk.x == mousePos.x && chunk.z == mousePos.z) {
                hoveredChunk = chunk;
                break;
            }
        }
    }

    @Override
    public List<String> getTooltip() {
        return hoveredChunk != null ? hoveredChunk.tooltips : null;
    }

    public void refresh() {
        if (lastVisibleBounds == null) return;
        visibleChunks = VCClientCache.instance.getChunkClaimsInArea(lastDimensionID, lastVisibleBounds);
        claimedMap = new Long2ShortOpenHashMap(visibleChunks.size());
        claimedMap.defaultReturnValue((short) -1);
        for (FTBChunkClaimPosition chunk : visibleChunks) {
            claimedMap.put(pack(chunk.x, chunk.z), chunk.uid);
        }
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }


    private boolean isSameTeam(int x, int z, short uid) {
        return claimedMap.get(pack(x, z)) == uid;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}