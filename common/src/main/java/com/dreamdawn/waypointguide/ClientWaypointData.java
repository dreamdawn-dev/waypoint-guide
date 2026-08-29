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
    private static boolean syncedThisSession = false;

    public static Consumer<List<Waypoint>> onWaypointsRemoved;

    private ClientWaypointData() {}

    public static synchronized void setWaypoints(List<Waypoint> wps) {
        List<Waypoint> removed = new ArrayList<>();
        if (syncedThisSession) {
            Set<String> newIds = new HashSet<>();
            for (Waypoint wp : wps) {
                newIds.add(wp.getId());
            }
            for (Waypoint old : waypoints) {
                if (!newIds.contains(old.getId())) {
                    removed.add(old);
                }
            }
        }
        waypoints.clear();
        waypoints.addAll(wps);
        syncedThisSession = true;
        if (!removed.isEmpty() && onWaypointsRemoved != null) {
            onWaypointsRemoved.accept(removed);
        }
    }

    public static synchronized void clear() {
        waypoints.clear();
        syncedThisSession = false;
    }

    public static synchronized List<Waypoint> getWaypoints() {
        return new ArrayList<>(waypoints);
    }
}
