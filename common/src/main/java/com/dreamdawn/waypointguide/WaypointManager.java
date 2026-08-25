package com.dreamdawn.waypointguide;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 服务端标记管理器，线程安全，管理每个玩家的标记点列表
 */
public final class WaypointManager {
    private static final ConcurrentHashMap<UUID, List<Waypoint>> PLAYER_WAYPOINTS = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path saveFile = null;

    public static BiConsumer<ServerPlayer, WaypointSyncPacket> syncCallback;

    private WaypointManager() {}

    public static List<Waypoint> getWaypoints(UUID playerId) {
        return PLAYER_WAYPOINTS.computeIfAbsent(playerId, k -> new ArrayList<>());
    }

    public static boolean addWaypoint(ServerPlayer player, Waypoint waypoint) {
        List<Waypoint> list = getWaypoints(player.getUUID());
        synchronized (list) {
            for (Waypoint wp : list) {
                if (wp.getId().equals(waypoint.getId())) {
                    return false;
                }
            }
            list.add(waypoint);
        }
        syncToPlayer(player);
        saveAll();
        return true;
    }

    public static boolean removeWaypoint(ServerPlayer player, String id) {
        List<Waypoint> list = getWaypoints(player.getUUID());
        synchronized (list) {
            boolean removed = list.removeIf(wp -> wp.getId().equals(id));
            if (removed) {
                syncToPlayer(player);
                saveAll();
            }
            return removed;
        }
    }

    public static void clearWaypoints(ServerPlayer player) {
        List<Waypoint> list = getWaypoints(player.getUUID());
        synchronized (list) {
            list.clear();
        }
        syncToPlayer(player);
        saveAll();
    }

    public static void syncToPlayer(ServerPlayer player) {
        List<Waypoint> list = getWaypoints(player.getUUID());
        List<Waypoint> snapshot;
        synchronized (list) {
            snapshot = new ArrayList<>(list);
        }
        if (syncCallback != null) {
            syncCallback.accept(player, new WaypointSyncPacket(snapshot));
        }
    }

    public static void loadAll(MinecraftServer server) {
        PLAYER_WAYPOINTS.clear();
        saveFile = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).getParent().resolve("waypoints.json");
        if (Files.exists(saveFile)) {
            try (Reader reader = Files.newBufferedReader(saveFile)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    UUID playerId = UUID.fromString(entry.getKey());
                    JsonArray arr = entry.getValue().getAsJsonArray();
                    List<Waypoint> list = new ArrayList<>();
                    for (JsonElement el : arr) {
                        list.add(Waypoint.fromJson(el.getAsJsonObject()));
                    }
                    PLAYER_WAYPOINTS.put(playerId, list);
                }
            } catch (IOException e) {
                System.err.println("[WaypointGuide] Failed to load waypoints: " + e.getMessage());
            }
        }
    }

    public static void saveAll() {
        if (saveFile == null) return;
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, List<Waypoint>> entry : PLAYER_WAYPOINTS.entrySet()) {
                JsonArray arr = new JsonArray();
                List<Waypoint> list = entry.getValue();
                synchronized (list) {
                    for (Waypoint wp : list) {
                        arr.add(wp.toJson());
                    }
                }
                root.add(entry.getKey().toString(), arr);
            }
            try (Writer writer = Files.newBufferedWriter(saveFile)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            System.err.println("[WaypointGuide] Failed to save waypoints: " + e.getMessage());
        }
    }
}
