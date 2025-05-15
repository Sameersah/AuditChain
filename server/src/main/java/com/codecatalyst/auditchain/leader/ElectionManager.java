package com.codecatalyst.auditchain.leader;

import com.codecatalyst.auditchain.config.Config;
import com.codecatalyst.auditchain.grpc.FileAuditServiceImpl;

import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.TriggerElectionRequest;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.TriggerElectionResponse;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.NotifyLeadershipRequest;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.NotifyLeadershipResponse;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainServiceGrpc;
import com.codecatalyst.auditchain.storage.BlockStorage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

public class ElectionManager {
    private static final int HEARTBEAT_TIMEOUT_MS = 10_000;  // 10 seconds
    private static final int ELECTION_CHECK_INTERVAL_MS = 30_000; // 5 seconds

    private static final Map<String, Long> lastHeartbeats = new ConcurrentHashMap<>();
    private static String currentLeader = null;
    private static int currentTerm = 0;
    private static final Map<String, Long> peerLatestBlockIds = new ConcurrentHashMap<>();

    public static void updateLatestBlockId(String node, long blockId) {
        peerLatestBlockIds.put(node, blockId);
    }

    public static long getHighestKnownBlockId() {
        return peerLatestBlockIds.values().stream().mapToLong(Long::longValue).max().orElse(-1);
    }

    public static synchronized int getCurrentTerm() {
        return currentTerm;
    }

    public static synchronized void updateTerm(int newTerm) {
        if (newTerm > currentTerm) {
            currentTerm = newTerm;
        }
    }

    public static synchronized void setCurrentLeader(String leader) {
        currentLeader = leader;
        System.out.println("🔄 Leader updated to: " + leader);
    }

    public static void updateHeartbeat(String address) {
        lastHeartbeats.put(address, Instant.now().toEpochMilli());
    }

    private static boolean isLeaderMissing() {

        if(Objects.equals(currentLeader, Config.NODE_ID)) {
            System.out.println("🔍 currentLeader is self (" + currentLeader + "). Leader is not missing.");
            return false;
        }
        if (currentLeader == null) {
            System.out.println("🔍 currentLeader is null → leader is missing.");
            return true;
        }

        if (!lastHeartbeats.containsKey(currentLeader)) {
            System.out.println("🔍 lastHeartbeats does not contain currentLeader (" + currentLeader + ") → leader is missing.");
            return true;
        }

        long lastSeen = lastHeartbeats.get(currentLeader);
        long now = Instant.now().toEpochMilli();
        long delta = now - lastSeen;

        System.out.println("🔍 Last heartbeat from leader " + currentLeader + ": " + lastSeen);
        System.out.println("🔍 Current time: " + now);
        System.out.println("🔍 Time since last heartbeat: " + delta + " ms");
        System.out.println("🔍 HEARTBEAT_TIMEOUT_MS: " + HEARTBEAT_TIMEOUT_MS);

        if (delta > HEARTBEAT_TIMEOUT_MS) {
            System.out.println("🔍 Time since last heartbeat exceeds threshold → leader is missing.");
            return true;
        }

        System.out.println("✅ Leader " + currentLeader + " is alive.");
        return false;
    }


    public static void startElectionMonitor(String selfAddress) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (isLeaderMissing()) {
                    System.out.println("⚠️ Leader is missing or unknown. Triggering election...");
                    triggerElection(selfAddress);
                }
            } catch (Exception e) {
                System.err.println("❌ Error during election check: " + e.getMessage());
            }
        }, 30, ELECTION_CHECK_INTERVAL_MS / 1000, TimeUnit.SECONDS);
    }

    private static void triggerElection(String selfAddress) {
        int selfBlockId = (int) BlockStorage.getLatestBlockId();
        int selfMempoolSize = FileAuditServiceImpl.getMempool().size();

        int votes = 1; // Vote for self
        currentTerm++;

        for (String peer : Config.PEER_ADDRESSES) {
            try {
                ManagedChannel channel = ManagedChannelBuilder.forTarget(peer).usePlaintext().build();
                BlockChainServiceGrpc.BlockChainServiceBlockingStub stub = BlockChainServiceGrpc.newBlockingStub(channel);

                TriggerElectionRequest req = TriggerElectionRequest.newBuilder()
                        .setTerm(currentTerm)
                        .setAddress(selfAddress)
                        .build();

                TriggerElectionResponse resp = stub.triggerElection(req);
                if (resp.getVote()) {
                    votes++;
                    System.out.println("✅ Vote received from: " + peer);
                } else {
                    System.out.println("❌ Vote denied from: " + peer);
                }

                channel.shutdown();
            } catch (Exception e) {
                System.err.println("❌ Failed to request vote from " + peer + ": " + e.getMessage());
            }
        }

        int majority = (Config.PEER_ADDRESSES.size() + 1) / 2 + 1;
        if (votes >= majority) {
            System.out.println("🗳️ Election won. Becoming new leader.");
            currentLeader = selfAddress;

            // Notify all peers
            for (String peer : Config.PEER_ADDRESSES) {
                try {
                    ManagedChannel channel = ManagedChannelBuilder.forTarget(peer).usePlaintext().build();
                    BlockChainServiceGrpc.BlockChainServiceBlockingStub stub = BlockChainServiceGrpc.newBlockingStub(channel);

                    NotifyLeadershipRequest notifyReq = NotifyLeadershipRequest.newBuilder()
                            .setAddress(selfAddress)
                            .build();

                    NotifyLeadershipResponse resp = stub.notifyLeadership(notifyReq);
                    System.out.println("📢 Notified " + peer + " => " + resp.getStatus());

                    channel.shutdown();
                } catch (Exception e) {
                    System.err.println("❌ Failed to notify " + peer + ": " + e.getMessage());
                }
            }
        } else {
            System.err.println("❌ Election lost. Votes received: " + votes);
        }
    }

    public static String getCurrentLeader() {
        return currentLeader;
    }

}
