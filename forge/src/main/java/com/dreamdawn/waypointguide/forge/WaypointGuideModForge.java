package com.dreamdawn.waypointguide.forge;

import com.dreamdawn.waypointguide.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Forge 平台模组入口
 */
@Mod("waypointguide")
public class WaypointGuideModForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("WaypointGuide");
    private static final String PROTOCOL_VERSION = "1";
    private static final double DISMISS_CLICK_RANGE = 5.0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("waypointguide", "network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public WaypointGuideModForge() {
        LOGGER.info("WaypointGuide mod initializing...");

        CHANNEL.registerMessage(0, WaypointSyncPacket.class,
                WaypointSyncPacket::encode, WaypointSyncPacket::new,
                (packet, ctx) -> {
                    ctx.get().enqueueWork(() -> ClientWaypointData.setWaypoints(packet.getWaypoints()));
                    ctx.get().setPacketHandled(true);
                });
        CHANNEL.registerMessage(1, WaypointDismissPacket.class,
                WaypointDismissPacket::encode, WaypointDismissPacket::new,
                (packet, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        ServerPlayer sender = ctx.get().getSender();
                        if (sender != null) {
                            handleDismissRequest(sender, packet.getId());
                        }
                    });
                    ctx.get().setPacketHandled(true);
                });

        WaypointManager.syncCallback = (player, packet) ->
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), packet);

        MinecraftForge.EVENT_BUS.register(this);

        WaypointRenderer.dismissRequestCallback = id -> CHANNEL.sendToServer(new WaypointDismissPacket(id));

        LOGGER.info("WaypointGuide mod initialized successfully!");
    }

    /** 服务端处理右键消除请求：校验后删除并广播事件 */
    private static void handleDismissRequest(ServerPlayer player, String id) {
        List<Waypoint> list = WaypointManager.getWaypoints(player.getUUID());
        Waypoint target = null;
        synchronized (list) {
            for (Waypoint wp : list) {
                if (wp.getId().equals(id)) {
                    target = wp;
                    break;
                }
            }
        }

        if (target == null || !target.isInteractive()) return;
        if (!target.getDimension().equals(player.level().dimension().location().toString())) return;

        Vec3 playerPos = player.position();
        double distSq = playerPos.distanceToSqr(target.getX(), target.getY(), target.getZ());
        if (distSq > DISMISS_CLICK_RANGE * DISMISS_CLICK_RANGE) return;

        if (WaypointManager.removeWaypoint(player, id)) {
            MinecraftForge.EVENT_BUS.post(new WaypointDismissEvent(
                    player, target.getId(), target.getName(),
                    target.getX(), target.getY(), target.getZ(),
                    target.getColor(), target.getNameColor(), target.getDimension()));
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
        LOGGER.info("WaypointGuide commands registered");
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WaypointManager.syncToPlayer(player);
            LOGGER.info("Synced waypoints to player: {}", player.getName().getString());
        }
    }

    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            WaypointManager.loadAll(level.getServer());
            LOGGER.info("Loaded waypoints from disk");
        }
    }

    @SubscribeEvent
    public void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            WaypointManager.saveAll();
        }
    }

    @Mod.EventBusSubscriber(modid = "waypointguide", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onRenderHUD(RenderGuiOverlayEvent.Post event) {
            if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()) {
                WaypointRenderer.render(event.getGuiGraphics(), event.getPartialTick());
            }
        }

        @SubscribeEvent
        public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
            if (event.isUseItem() && WaypointRenderer.handleRightClickCrosshair()) {
                event.setCanceled(true);
                event.setSwingHand(true);
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientWaypointData.clear();
            WaypointRenderer.onWorldLeave();
        }
    }
}
