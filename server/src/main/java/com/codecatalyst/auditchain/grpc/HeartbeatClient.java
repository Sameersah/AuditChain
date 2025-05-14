// HeartbeatClient.java
package com.codecatalyst.auditchain.grpc;

import com.codecatalyst.auditchain.config.Config;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainServiceGrpc;
import com.codecatalyst.auditchain.storage.BlockStorage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.codecatalyst.auditchain.leader.ElectionManager;

public class  HeartbeatClient {

    public static void broadcastHeartbeat() {
        int latestBlockId = (int) BlockStorage.getLatestBlockId();
        int mempoolSize = FileAuditServiceImpl.getMempool().size();

        for (String peer : Config.PEER_ADDRESSES) {
            if (peer.equals(Config.NODE_ID)) continue; // Avoid sending heartbeat to self

            try {
                ManagedChannel channel = ManagedChannelBuilder.forTarget(peer)
                        .usePlaintext()
                        .build();

                BlockChainServiceGrpc.BlockChainServiceBlockingStub stub = BlockChainServiceGrpc.newBlockingStub(channel);

                BlockChainProto.HeartbeatRequest request = BlockChainProto.HeartbeatRequest.newBuilder()
                        .setFromAddress(Config.NODE_ID)
                        .setCurrentLeaderAddress(ElectionManager.getCurrentLeader())
                        .setLatestBlockId(latestBlockId)
                        .setMemPoolSize(mempoolSize)
                        .build();

                BlockChainProto.HeartbeatResponse response = stub.sendHeartbeat(request);

                if (!"success".equalsIgnoreCase(response.getStatus())) {
                    System.err.println("⚠️ Heartbeat to " + peer + " failed: " + response.getErrorMessage());
                } else {
                    System.out.println("💓 Heartbeat sent to " + peer + " (BlockID: " + latestBlockId + ", Mempool: " + mempoolSize + ")");
                }

                channel.shutdown();

            } catch (Exception e) {
                System.err.println("❌ Heartbeat to " + peer + " failed: " + e.getMessage());
            }
        }
    }

}
