package com.dreamdawn.waypointguide.forge;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

public class WaypointDismissEvent extends Event {
    private final Player player;
    private final String waypointId;
    private final String waypointName;
    private final double x;
    private final double y;
    private final double z;
    private final int color;
    private final int nameColor;
    private final String dimension;

    public WaypointDismissEvent(Player player, String waypointId, String waypointName,
                                double x, double y, double z,
                                int color, int nameColor, String dimension) {
        this.player = player;
        this.waypointId = waypointId;
        this.waypointName = waypointName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.nameColor = nameColor;
        this.dimension = dimension;
    }

    public Player getPlayer() { return player; }
    public String getWaypointId() { return waypointId; }
    public String getWaypointName() { return waypointName; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public int getColor() { return color; }
    public int getNameColor() { return nameColor; }
    public String getDimension() { return dimension; }
}
