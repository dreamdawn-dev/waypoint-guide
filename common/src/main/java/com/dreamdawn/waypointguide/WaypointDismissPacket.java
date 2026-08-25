package com.dreamdawn.waypointguide;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 客户端 -> 服务端的右键消除请求包，仅携带标记 ID
 */
public class WaypointDismissPacket {
    private final String id;

    public WaypointDismissPacket(String id) {
        this.id = id;
    }

    public WaypointDismissPacket(FriendlyByteBuf buf) {
        this.id = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id);
    }

    public String getId() {
        return id;
    }
}
