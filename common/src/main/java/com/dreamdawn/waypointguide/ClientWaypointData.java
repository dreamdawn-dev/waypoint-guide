package com.dreamdawn.waypointguide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 客户端标记数据存储，线程安全的
 */
public final class ClientWaypointData {
    private static final List<Waypoint> waypoints = new ArrayList<>();

    public static Consumer<List<Waypoint>> onWaypointsRemoved;

    private ClientWaypointData() {}

    public static synchronized void setWaypoints(List<Waypoint> wps) {
        Set<String> newIds = new HashSet<>();
        for (Waypoint wp : wps) {
            newIds.add(wp.getId());
        }
        List<Waypoint> removed = new ArrayList<>();
        for (Waypoint old : waypoints) {
            if (!newIds.contains(old.getId())) {
                removed.add(old);
            }
        }
        waypoints.clear();
        waypoints.addAll(wps);
        if (!removed.isEmpty() && onWaypointsRemoved != null) {
            onWaypointsRemoved.accept(removed);
        }
    }

    public static synchronized List<Waypoint> getWaypoints() {
        return new ArrayList<>(waypoints);
    }
}
