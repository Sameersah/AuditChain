package com.codecatalyst.auditchain.recovery;

import com.codecatalyst.auditchain.config.Config;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainServiceGrpc;
import com.codecatalyst.auditchain.storage.BlockStorage;
import com.codecatalyst.auditchain.leader.ElectionManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Map;
import java.util.concurrent.*;

public class NodeRecoveryManager {

    private static final int SYNC_INTERVAL_SECONDS = 30;

    public static void startRecoveryMonitor() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                long localBlockId = BlockStorage.getLatestBlockId();
                long maxKnownBlockId = ElectionManager.getHighestKnownBlockId();

                System.out.println("🔁 [Recovery] Local block ID: " + localBlockId + ", Max cluster block ID: " + maxKnownBlockId);

                if (maxKnownBlockId > localBlockId) {
                    for (long i = localBlockId + 1; i <= maxKnownBlockId; i++) {
                        syncBlockFromPeers(i);
                    }
                }

            } catch (Exception e) {
                System.err.println("❌ [Recovery] Error during block sync: " + e.getMessage());
            }
        }, 10, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private static void syncBlockFromPeers(long blockId) {
        for (String peer : Config.PEER_ADDRESSES) {
            try {
                ManagedChannel channel = ManagedChannelBuilder.forTarget(peer).usePlaintext().build();
                BlockChainServiceGrpc.BlockChainServiceBlockingStub stub = BlockChainServiceGrpc.newBlockingStub(channel);

                BlockChainProto.GetBlockRequest request = BlockChainProto.GetBlockRequest.newBuilder()
                        .setId(blockId)
                        .build();

                BlockChainProto.GetBlockResponse response = stub.getBlock(request);
                channel.shutdown();

                if (response.getStatus().equalsIgnoreCase("success")) {
                    boolean saved = BlockStorage.saveBlock(response.getBlock());
                    if (saved) {
                        System.out.println("✅ [Recovery] Synced block " + blockId + " from " + peer);
                    }
                    return;
                } else {
                    System.out.println("⚠️ [Recovery] Peer " + peer + " failed to provide block " + blockId);
                }
            } catch (Exception e) {
                System.err.println("❌ [Recovery] Error syncing block " + blockId + " from peer " + peer + ": " + e.getMessage());
            }
        }

        System.err.println("❌ [Recovery] Failed to sync block " + blockId + " from any peer.");
    }
}
