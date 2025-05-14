package com.codecatalyst.auditchain.election;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderStateManager {
    private static String currentLeader = null;
    private static int currentTerm = 0;

    private static final Map<String, Long> heartbeatTimestamps = new ConcurrentHashMap<>();

    public static synchronized void setCurrentLeader(String leader) {
        currentLeader = leader;
        System.out.println("🔄 Leader updated to: " + leader);
    }

    public static synchronized String getCurrentLeader() {
        return currentLeader;
    }

    public static synchronized int getCurrentTerm() {
        return currentTerm;
    }

    public static synchronized void updateTerm(int newTerm) {
        if (newTerm > currentTerm) {
            currentTerm = newTerm;
        }
    }

    public static void updateHeartbeat(String address) {
        heartbeatTimestamps.put(address, Instant.now().toEpochMilli());
    }

    public static Map<String, Long> getHeartbeatMap() {
        return heartbeatTimestamps;
    }

    public static boolean isLeaderAlive(String leaderAddress, long timeoutMillis) {
        Long last = heartbeatTimestamps.get(leaderAddress);
        if (last == null) return false;
        return Instant.now().toEpochMilli() - last < timeoutMillis;
    }
}
