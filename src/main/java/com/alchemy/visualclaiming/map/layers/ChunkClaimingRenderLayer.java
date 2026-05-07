package com.alchemy.visualclaiming.map.layers;

import com.alchemy.visualclaiming.map.DrawUtilsExtra;
import com.alchemy.visualclaiming.database.FTBChunkClaimPosition;
import com.alchemy.visualclaiming.database.VCClientCache;
import com.feed_the_beast.ftbutilities.net.MessageClaimedChunksModify;
import hellfall.visualores.map.DrawUtils;
import hellfall.visualores.map.layers.RenderLayer;
import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.input.Keyboard;

import java.util.*;

@Mod.EventBusSubscriber
public class ChunkClaimingRenderLayer extends RenderLayer {

    public static ChunkClaimingRenderLayer instance;

    private List<FTBChunkClaimPosition> visibleChunks = new ArrayList<>();
    private Long2ShortOpenHashMap claimedMap = new Long2ShortOpenHashMap();
    private FTBChunkClaimPosition hoveredChunk;
    private ChunkPos selectedChunk;
    private int lastDimensionID;
    private int[] lastVisibleBounds;
    private boolean dirty = false;

    public ChunkClaimingRenderLayer(String key) {
        super(key);
        instance = this;
    }

    @Override
    public void render(double cameraX, double cameraZ, double scale) {
        if (dirty) refresh();

        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null) {
            int px = player.chunkCoordX;
            int pz = player.chunkCoordZ;
            DrawUtils.drawOverlayBox((px - 7) * 16, (pz - 7) * 16, (px + 8) * 16, (pz + 8) * 16, 0xFFFFFFFF, 0x00FFFFFF);
        }

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
        selectedChunk = null;

        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null) {
            int px = player.chunkCoordX;
            int pz = player.chunkCoordZ;
            if (mousePos.x >= px - 7 && mousePos.x <= px + 7 && mousePos.z >= pz - 7 && mousePos.z <= pz + 7) {
                selectedChunk = mousePos;
            }
        }

        for (FTBChunkClaimPosition chunk : visibleChunks) {
            if (chunk.x == mousePos.x && chunk.z == mousePos.z) {
                hoveredChunk = chunk;
                break;
            }
        }
    }

    @Override
    public boolean onClick() {
        if (selectedChunk == null) return false;

        boolean shiftHeld = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        boolean ctrlHeld  = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        int selectionMode;
        if (ctrlHeld) {
            selectionMode = shiftHeld ? MessageClaimedChunksModify.UNLOAD : MessageClaimedChunksModify.LOAD;
        } else {
            selectionMode = shiftHeld ? MessageClaimedChunksModify.UNCLAIM : MessageClaimedChunksModify.CLAIM;
        }

        new MessageClaimedChunksModify(selectedChunk.x, selectedChunk.z, selectionMode, Collections.singleton(selectedChunk)).sendToServer();
        Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1f));
        return true;
    }

    @Override
    public List<String> getTooltip() {
        List<String> tooltips = new ArrayList<>();
        if (hoveredChunk != null) {
            tooltips.addAll(hoveredChunk.tooltips);
        } else if (selectedChunk != null) {
            tooltips.add(I18n.format("visualclaiming.wilderness"));
        }
        return tooltips.isEmpty() ? null : tooltips;
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