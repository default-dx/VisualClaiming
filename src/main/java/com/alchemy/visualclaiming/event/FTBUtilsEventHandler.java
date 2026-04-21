package com.alchemy.visualclaiming.event;

import com.alchemy.visualclaiming.database.VCClientCache;
import com.feed_the_beast.ftblib.lib.client.ClientUtils;
import com.feed_the_beast.ftblib.lib.gui.misc.ChunkSelectorMap;
import com.feed_the_beast.ftblib.lib.math.MathUtils;
import com.feed_the_beast.ftbutilities.events.chunks.UpdateClientDataEvent;
import com.feed_the_beast.ftbutilities.gui.ClientClaimedChunks;
import com.feed_the_beast.ftbutilities.net.MessageClaimedChunksRequest;
import com.feed_the_beast.ftbutilities.net.MessageClaimedChunksUpdate;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber()
@SuppressWarnings("unused")
public class FTBUtilsEventHandler {
    private static final Long2IntOpenHashMap cooldowns = new Long2IntOpenHashMap();
    // 5 seconds at 20 TPS
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player.world.isRemote || !(event.player instanceof EntityPlayerMP mp)) return;
        new MessageClaimedChunksRequest(mp).sendToServer();
    }

    @SubscribeEvent
    public static void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        if (event.getEntityPlayer().world.isRemote || !(event.getEntityPlayer() instanceof EntityPlayerMP mp)) return;
        new MessageClaimedChunksRequest(mp).sendToServer();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        int INTERVAL_TICKS = 100;

        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) return;

        EntityPlayerMP mp = (EntityPlayerMP) event.player;
        long key = mp.getUniqueID().getLeastSignificantBits();

        int remaining = cooldowns.getOrDefault(key, 0) - 1;
        cooldowns.put(key, remaining);

        if (remaining > 0) return;

        cooldowns.put(key, INTERVAL_TICKS);
        new MessageClaimedChunksRequest(mp).sendToServer();
    }

    // Base on FTB Utilities JourneyMap integration, adapt for VisualOres Layer
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

    }
}
