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

        System.out.println("🔁 Starting heartbeat broadcast...");
        System.out.println("🧠 Self NODE_ID: " + Config.NODE_ID);
        System.out.println("👑 Current Leader: " + ElectionManager.getCurrentLeader());
        System.out.println("📦 Latest Block ID: " + latestBlockId + ", 🧾 Mempool Size: " + mempoolSize);

        for (String peer : Config.PEER_ADDRESSES) {
            System.out.println("🔗 Preparing to contact peer: " + peer);

            if (peer.equals(Config.NODE_ID)) {
                System.out.println("⏩ Skipping self (" + peer + ")");
                continue;
            }

            try {
                ManagedChannel channel = ManagedChannelBuilder
                        .forTarget(peer)
                        .usePlaintext()
                        .build();

                BlockChainServiceGrpc.BlockChainServiceBlockingStub stub =
                        BlockChainServiceGrpc.newBlockingStub(channel);

                BlockChainProto.HeartbeatRequest request = BlockChainProto.HeartbeatRequest.newBuilder()
                        .setFromAddress(Config.NODE_ID)
                        .setCurrentLeaderAddress(ElectionManager.getCurrentLeader() == null ? "" : ElectionManager.getCurrentLeader())
                        .setLatestBlockId(latestBlockId)
                        .setMemPoolSize(mempoolSize)
                        .build();

                System.out.println("📤 Sending heartbeat to " + peer + "...");
                BlockChainProto.HeartbeatResponse response = stub.sendHeartbeat(request);

                if (!"success".equalsIgnoreCase(response.getStatus())) {
                    System.err.println("⚠️ Heartbeat failed for peer " + peer + " → " + response.getErrorMessage());
                } else {
                    System.out.println("✅ Heartbeat acknowledged by " + peer);
                }

                channel.shutdown();

            } catch (Exception e) {
                System.err.println("❌ Error contacting peer " + peer);
                e.printStackTrace();  // Print full stack trace for deeper visibility
            }
        }

        System.out.println("✅ Heartbeat broadcast complete.\n");
    }


}
