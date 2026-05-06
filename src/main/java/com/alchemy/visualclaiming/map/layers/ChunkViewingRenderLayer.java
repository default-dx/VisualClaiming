package com.alchemy.visualclaiming.map.layers;

import com.alchemy.visualclaiming.database.FTBChunkClaimPosition;
import com.alchemy.visualclaiming.database.VCClientCache;
import com.alchemy.visualclaiming.map.DrawUtilsExtra;
import com.feed_the_beast.ftblib.lib.client.ClientUtils;
import com.feed_the_beast.ftblib.lib.gui.misc.ChunkSelectorMap;
import com.feed_the_beast.ftbutilities.events.chunks.UpdateClientDataEvent;
import com.feed_the_beast.ftbutilities.gui.ClientClaimedChunks;
import com.feed_the_beast.ftbutilities.net.MessageClaimedChunksUpdate;
import hellfall.visualores.map.DrawUtils;
import hellfall.visualores.map.layers.RenderLayer;
import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Mod.EventBusSubscriber()
public class ChunkViewingRenderLayer extends RenderLayer {
    private static boolean dirty = false;
    public List<FTBChunkClaimPosition> visibleChunks = new ArrayList<>();
    private Long2ShortOpenHashMap claimedMap = new Long2ShortOpenHashMap();
    private FTBChunkClaimPosition hoveredChunk;
    public ChunkViewingRenderLayer(String key) {
        super(key);
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

    private boolean isSameTeam(int x, int z, short uid) {
        return claimedMap.get(pack(x, z)) == uid;
    }

    @SubscribeEvent
    public static void onDataReceived(UpdateClientDataEvent event) {
        MessageClaimedChunksUpdate message = event.getMessage();
        int dim = ClientUtils.getDim();
        ClientClaimedChunks.ChunkData[] data = new ClientClaimedChunks.ChunkData[ChunkSelectorMap.TILES_GUI * ChunkSelectorMap.TILES_GUI];
        for (ClientClaimedChunks.Team team : message.teams.values()) {
            for (Map.Entry<Integer, ClientClaimedChunks.ChunkData> entry : team.chunks.entrySet())
            {
                int x = entry.getKey() % ChunkSelectorMap.TILES_GUI;
                int z = entry.getKey() / ChunkSelectorMap.TILES_GUI;
                ClientClaimedChunks.ChunkData chunkData = entry.getValue();
                data[x + z * ChunkSelectorMap.TILES_GUI] = chunkData;
            }
        }

        for (int z = 0; z < ChunkSelectorMap.TILES_GUI; z++)
        {
            for (int x = 0; x < ChunkSelectorMap.TILES_GUI; x++)
            {
                ChunkPos pos = new ChunkPos(message.startX + x, message.startZ + z);
                ClientClaimedChunks.ChunkData chunkData = data[x + z * ChunkSelectorMap.TILES_GUI];
                if (chunkData == null) {
                    VCClientCache.instance.removeChunkData(dim, pos);
                } else {
                    VCClientCache.instance.addChunkData(dim, pos,
                            chunkData.team.uid,
                            chunkData.flags,
                            chunkData.team.color.getColor().hashCode(),
                            chunkData.team.nameComponent.getFormattedText());
                }
            }
        }
        dirty = true;
    }

}
