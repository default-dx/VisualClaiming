package com.alchemy.visualclaiming.map.layers;

import com.alchemy.visualclaiming.VisualClaiming;
import com.alchemy.visualclaiming.map.DrawUtilsExtra;
import com.alchemy.visualclaiming.database.FTBChunkClaimPosition;
import com.alchemy.visualclaiming.database.VCClientCache;
import com.feed_the_beast.ftblib.lib.client.ClientUtils;
import com.feed_the_beast.ftblib.lib.gui.misc.ChunkSelectorMap;
import com.feed_the_beast.ftbutilities.events.chunks.UpdateClientDataEvent;
import com.feed_the_beast.ftbutilities.gui.ClientClaimedChunks;
import com.feed_the_beast.ftbutilities.net.MessageClaimedChunksModify;
import com.feed_the_beast.ftbutilities.net.MessageClaimedChunksUpdate;
import hellfall.visualores.map.DrawUtils;
import hellfall.visualores.map.layers.RenderLayer;
import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Level;
import org.lwjgl.input.Keyboard;

import java.util.*;


@Mod.EventBusSubscriber()
public class ChunkClaimingRenderLayer extends RenderLayer {
    public List<FTBChunkClaimPosition> visibleChunks = new ArrayList<>();
    public static boolean dirty = false;

    private int lastDimensionID;
    private int[] lastVisibleBounds;
    private Long2ShortOpenHashMap claimedMap = new Long2ShortOpenHashMap();
    private FTBChunkClaimPosition hoveredChunk;
    private ChunkPos selectedChunk;
    public ChunkClaimingRenderLayer(String key) {
        super(key);
    }

    @Override
    public void render(double cameraX, double cameraZ, double scale) {
        if (dirty && lastVisibleBounds != null) {
            updateVisibleArea(lastDimensionID, lastVisibleBounds);
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null) {
            int playerX = player.chunkCoordX;
            int playerZ = player.chunkCoordZ;
            DrawUtils.drawOverlayBox((playerX - 7) * 16, (playerZ - 7) * 16, (playerX + 8) * 16, (playerZ + 8) * 16,0xFFFFFFFF ,0x00000000);
        };
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
        lastDimensionID = dimensionID;
        lastVisibleBounds = visibleBounds;
        visibleChunks = VCClientCache.instance.getChunkClaimsInArea(dimensionID, visibleBounds);
        claimedMap = new Long2ShortOpenHashMap(visibleChunks.size());
        claimedMap.defaultReturnValue((short) -1);
        for (FTBChunkClaimPosition chunk : visibleChunks) {
            claimedMap.put(pack(chunk.x, chunk.z), chunk.uid);
        }
        dirty = false;
    }

    @Override
    public void updateHovered(double mouseX, double mouseY, double cameraX, double cameraZ, double scale) {
        ChunkPos mousePos = new ChunkPos(DrawUtils.getMouseBlockPos(mouseX, mouseY, cameraX, cameraZ, scale));
        hoveredChunk = null;
        selectedChunk = null;

        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null) {
            int playerX = player.chunkCoordX;
            int playerZ = player.chunkCoordZ;
            if ((mousePos.x >= playerX - 7 && mousePos.x <= playerX + 7) && (mousePos.z >= playerZ - 7 && mousePos.z <= playerZ + 7)) {
                selectedChunk = mousePos;
            }
        }
        for (FTBChunkClaimPosition chunkClaimPosition : visibleChunks) {
            if (chunkClaimPosition.x == mousePos.x && chunkClaimPosition.z == mousePos.z) {
                hoveredChunk = chunkClaimPosition;
                break;
            }
        }
    }

    @Override
    public boolean onClick() {
        boolean shiftHeld = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        boolean ctrlHeld  = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        int selectionMode;
        if (ctrlHeld) {
            selectionMode = shiftHeld ? MessageClaimedChunksModify.UNLOAD : MessageClaimedChunksModify.LOAD;
        } else {
            selectionMode = shiftHeld ? MessageClaimedChunksModify.UNCLAIM : MessageClaimedChunksModify.CLAIM;
        }
        if (selectedChunk != null) {
            Collection<ChunkPos> chunks = Collections.singleton(selectedChunk);
            new MessageClaimedChunksModify(selectedChunk.x, selectedChunk.z, selectionMode, chunks).sendToServer();
            return true;
        };
        return false;
    }

    @Override
    public List<String> getTooltip() {
        List<String> tooltips = new ArrayList<>();
        if (hoveredChunk != null) {
            tooltips.addAll(hoveredChunk.tooltips);
        }
        if (selectedChunk != null && hoveredChunk == null) {
            tooltips.add("Wilderness");
        }
        return tooltips.isEmpty() ? null : tooltips;
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
