package com.codecatalyst.auditchain.grpc;

import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainServiceGrpc;
import com.codecatalyst.auditchain.proto.common.CommonProto;
import io.grpc.stub.StreamObserver;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.Block;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.BlockVoteResponse;
import com.codecatalyst.auditchain.storage.BlockStorage;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.BlockCommitResponse;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.GetBlockResponse;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.HeartbeatResponse;



import io.grpc.stub.StreamObserver;


public class BlockChainServiceImpl extends BlockChainServiceGrpc.BlockChainServiceImplBase {

    private final Mempool mempool = new Mempool();

    @Override
    public void whisperAuditRequest(CommonProto.FileAudit audit, StreamObserver<BlockChainProto.WhisperResponse> responseObserver) {
        mempool.add(audit);  // assume already validated at source node

        BlockChainProto.WhisperResponse response = BlockChainProto.WhisperResponse.newBuilder()
                .setStatus("success")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void proposeBlock(Block request, StreamObserver<BlockVoteResponse> responseObserver) {
        System.out.println("🔷 Received block proposal: block_id = " + request.getId());

        // Verify each audit in the block
        boolean allValid = request.getAuditsList().stream().allMatch(SignatureVerifier::verify);

        BlockVoteResponse response = BlockVoteResponse.newBuilder()
                .setVote(allValid)
                .setStatus(allValid ? "success" : "failure")
                .setErrorMessage(allValid ? "" : "One or more audit signatures failed verification")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        System.out.println("🔶 Block proposal vote: " + (allValid ? "APPROVED ✅" : "REJECTED ❌"));
    }

    @Override
    public void commitBlock(Block request, StreamObserver<BlockChainProto.BlockCommitResponse> responseObserver) {
        System.out.println("📥 CommitBlock called for block_id: " + request.getId());

        boolean saved = BlockStorage.saveBlock(request);

        BlockChainProto.BlockCommitResponse response = BlockCommitResponse.newBuilder()
                .setStatus(saved ? "success" : "failure")
                .setErrorMessage(saved ? "" : "Failed to write block to disk")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getBlock(BlockChainProto.GetBlockRequest request, StreamObserver<GetBlockResponse> responseObserver) {
        long blockId = request.getId();
        System.out.println("📤 GetBlock called for block_id: " + blockId);

        BlockChainProto.GetBlockResponse.Builder responseBuilder = GetBlockResponse.newBuilder();

        try {
            Block block = BlockStorage.loadBlock(blockId);
            responseBuilder.setBlock(block)
                    .setStatus("success");
        } catch (Exception e) {
            responseBuilder.setStatus("failure")
                    .setErrorMessage("Block not found: " + e.getMessage());
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendHeartbeat(BlockChainProto.HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
        System.out.println("💓 Heartbeat received from: " + request.getFromAddress());
        System.out.println("  Leader reported: " + request.getCurrentLeaderAddress());
        System.out.println("  Latest Block ID: " + request.getLatestBlockId());
        System.out.println("  Mempool Size: " + request.getMemPoolSize());



        HeartbeatResponse response = BlockChainProto.HeartbeatResponse.newBuilder()
                .setStatus("success")
                .setErrorMessage("")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }




}
