package com.codecatalyst.auditchain.grpc;

import com.codecatalyst.auditchain.config.Config;
import com.codecatalyst.auditchain.proto.common.CommonProto;
import com.codecatalyst.auditchain.proto.fileaudit.FileAuditProto;
import com.codecatalyst.auditchain.proto.fileaudit.FileAuditServiceGrpc;
import io.grpc.stub.StreamObserver;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

public class FileAuditServiceImpl extends FileAuditServiceGrpc.FileAuditServiceImplBase {

    private final Mempool mempool = new Mempool();

    private final WhisperClient whisperClient = new WhisperClient(Config.PEER_ADDRESSES);

    public static Mempool getMempool() {
        return new FileAuditServiceImpl().mempool;
    }

    private String generateBlockchainTxHash(CommonProto.FileAudit audit) {
        try {
            // Create a string of all audit data
            String data = audit.getReqId() +
                    audit.getFileInfo().getFileId() +
                    audit.getFileInfo().getFileName() +
                    audit.getUserInfo().getUserId() +
                    audit.getUserInfo().getUserName() +
                    audit.getAccessType().name() +
                    audit.getTimestamp() +
                    audit.getPublicKey() +
                    audit.getSignature();

            // Generate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            System.err.println("Failed to generate blockchain tx hash: " + e.getMessage());
            return "";
        }
    }

    @Override
    public void submitAudit(CommonProto.FileAudit request, StreamObserver<FileAuditProto.FileAuditResponse> responseObserver) {
        // Log the received audit message
        System.out.println("\nReceived Audit Request:");
        System.out.println("Request ID: " + request.getReqId());
        System.out.println("File ID: " + request.getFileInfo().getFileId());
        System.out.println("File Name: " + request.getFileInfo().getFileName());
        System.out.println("User ID: " + request.getUserInfo().getUserId());
        System.out.println("User Name: " + request.getUserInfo().getUserName());
        System.out.println("Access Type: " + request.getAccessType());
        System.out.println("Timestamp: " + request.getTimestamp());
        System.out.println("Public Key: " + request.getPublicKey());
        System.out.println("Signature: " + request.getSignature() + "\n");

        boolean isValid = SignatureVerifier.verify(request);

        FileAuditProto.FileAuditResponse.Builder response = FileAuditProto.FileAuditResponse.newBuilder()
                .setReqId(request.getReqId());

        if (isValid) {
            mempool.add(request);
            response.setStatus("success");
            
            // Generate and set blockchain transaction hash
            String txHash = generateBlockchainTxHash(request);
            response.setBlockchainTxHash(txHash);
            System.out.println("Generated blockchain tx hash: " + txHash);

            // Print mempool contents
            mempool.printMempool();

            // ✅ Broadcast to other full nodes
            whisperClient.broadcastAudit(request);
        } else {
            response.setStatus("failure")
                    .setErrorMessage("Invalid signature.");
        }

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }
}
