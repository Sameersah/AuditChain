package com.codecatalyst.auditchain.grpc;

import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainServiceGrpc;
import com.codecatalyst.auditchain.proto.common.CommonProto;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.List;

public class WhisperClient {

    private final List<String> peerAddresses;

    public WhisperClient(List<String> peerAddresses) {
        this.peerAddresses = peerAddresses;
    }

    public void broadcastAudit(CommonProto.FileAudit audit) {
        for (String address : peerAddresses) {
            String[] parts = address.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(host, port)
                    .usePlaintext()  // No TLS for local testing
                    .build();

            BlockChainServiceGrpc.BlockChainServiceBlockingStub stub =
                    BlockChainServiceGrpc.newBlockingStub(channel);

            try {
                BlockChainProto.WhisperResponse response = stub.whisperAuditRequest(audit);
                System.out.printf("Whisper to %s successful: %s%n", address, response.getStatus());
            } catch (Exception e) {
                System.err.printf("Failed to whisper to %s: %s%n", address, e.getMessage());
            } finally {
                channel.shutdown();
            }
        }
    }
}
