package com.dreamdawn.waypointguide;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务器发给客户端的标记同步数据包，包含全量 Waypoint 列表
 */
public class WaypointSyncPacket {
    private final List<Waypoint> waypoints;

    public WaypointSyncPacket(List<Waypoint> waypoints) {
        this.waypoints = waypoints;
    }

    public WaypointSyncPacket(FriendlyByteBuf buf) {
        int count = buf.readInt();
        this.waypoints = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            waypoints.add(Waypoint.fromBuf(buf));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(waypoints.size());
        for (Waypoint wp : waypoints) {
            wp.toBuf(buf);
        }
    }

    public List<Waypoint> getWaypoints() {
        return waypoints;
    }
}
