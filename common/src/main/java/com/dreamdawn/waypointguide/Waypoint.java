package com.dreamdawn.waypointguide;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * 标记点数据类，包含唯一ID、名称、三维坐标、颜色和维度信息
 */
public class Waypoint {
    private final String id;
    private final String name;
    private final double x;
    private final double y;
    private final double z;
    private final int color;
    private final int nameColor;
    private final String dimension;
    private final boolean interactive;
    private final double maxRenderDistance;

    public Waypoint(String id, String name, double x, double y, double z, int color, int nameColor, String dimension) {
        this(id, name, x, y, z, color, nameColor, dimension, true);
    }

    public Waypoint(String id, String name, double x, double y, double z, int color, int nameColor, String dimension, boolean interactive) {
        this(id, name, x, y, z, color, nameColor, dimension, interactive, -1);
    }

    public Waypoint(String id, String name, double x, double y, double z, int color, int nameColor, String dimension, boolean interactive, double maxRenderDistance) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.nameColor = nameColor;
        this.dimension = dimension;
        this.interactive = interactive;
        this.maxRenderDistance = maxRenderDistance;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public int getColor() { return color; }
    public int getNameColor() { return nameColor; }
    public String getDimension() { return dimension; }
    public boolean isInteractive() { return interactive; }
    /** 可见距离上限，小于等于 0 表示无限制 */
    public double getMaxRenderDistance() { return maxRenderDistance; }

    public void toBuf(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(name);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(color);
        buf.writeInt(nameColor);
        buf.writeUtf(dimension);
        buf.writeBoolean(interactive);
        buf.writeDouble(maxRenderDistance);
    }

    public static Waypoint fromBuf(FriendlyByteBuf buf) {
        return new Waypoint(
                buf.readUtf(),
                buf.readUtf(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readDouble()
        );
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("name", name);
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("z", z);
        obj.addProperty("color", color);
        obj.addProperty("nameColor", nameColor);
        obj.addProperty("dimension", dimension);
        obj.addProperty("interactive", interactive);
        obj.addProperty("maxRenderDistance", maxRenderDistance);
        return obj;
    }

    public static Waypoint fromJson(JsonObject obj) {
        double maxRenderDistance = obj.has("maxRenderDistance") ? obj.get("maxRenderDistance").getAsDouble() : -1;
        return new Waypoint(
                obj.get("id").getAsString(),
                obj.get("name").getAsString(),
                obj.get("x").getAsDouble(),
                obj.get("y").getAsDouble(),
                obj.get("z").getAsDouble(),
                obj.get("color").getAsInt(),
                obj.get("nameColor").getAsInt(),
                obj.get("dimension").getAsString(),
                obj.get("interactive").getAsBoolean(),
                maxRenderDistance
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Waypoint waypoint)) return false;
        return Objects.equals(id, waypoint.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
