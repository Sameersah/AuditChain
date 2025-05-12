package com.codecatalyst.auditchain.grpc;

import com.codecatalyst.auditchain.config.Config;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainServiceGrpc;
import com.codecatalyst.auditchain.proto.common.CommonProto;
import com.codecatalyst.auditchain.util.HashUtil;
import com.codecatalyst.auditchain.util.MerkleUtil;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.Block;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.BlockVoteResponse;
import com.codecatalyst.auditchain.storage.BlockStorage;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.BlockCommitResponse;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.GetBlockResponse;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.HeartbeatResponse;



import io.grpc.stub.StreamObserver;

import java.util.Comparator;
import java.util.List;


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

        // Remove committed audits from the mempool if saved successfully
        if (saved) {
            for (CommonProto.FileAudit audit : request.getAuditsList()) {
                FileAuditServiceImpl.getMempool().removeAudit(audit.getReqId());
            }
            System.out.println("🧹 Removed committed audits from mempool.");
        }

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

    public void proposeBlockAsLeader() {
        try {
            // Step 1: Get audits from mempool
            List<CommonProto.FileAudit> mempoolAudits = Mempool.getAll();
            if (mempoolAudits.isEmpty()) {
                System.out.println("ℹ️ No audits in mempool. Skipping block proposal.");
                return;
            }

            // Step 2: Sort audits
            mempoolAudits.sort(Comparator.comparingLong(CommonProto.FileAudit::getTimestamp));

            // Step 3: Build block
            int blockId = BlockStorage.getNextBlockId();
            String previousHash = BlockStorage.getLastBlockHash();
            String merkleRoot = MerkleUtil.computeMerkleRoot(mempoolAudits);
            String hash = HashUtil.computeBlockHash(blockId, previousHash, mempoolAudits, merkleRoot);

            BlockChainProto.Block block = BlockChainProto.Block.newBuilder()
                    .setId(blockId)
                    .setHash(hash)
                    .setPreviousHash(previousHash)
                    .addAllAudits(mempoolAudits)
                    .setMerkleRoot(merkleRoot)
                    .build();

            // Step 4: Propose to all peers
            int totalPeers = Config.PEER_ADDRESSES.size();
            int positiveVotes = 0;

            for (String peer : Config.PEER_ADDRESSES) {
                try {
                    ManagedChannel channel = ManagedChannelBuilder.forTarget(peer)
                            .usePlaintext()
                            .build();
                    BlockChainServiceGrpc.BlockChainServiceBlockingStub stub = BlockChainServiceGrpc.newBlockingStub(channel);

                    BlockChainProto.BlockVoteResponse vote = stub.proposeBlock(block);
                    if (vote.getVote()) {
                        positiveVotes++;
                    }

                    System.out.println("✅ Vote from " + peer + ": " + vote.getVote());
                    channel.shutdown();
                } catch (Exception e) {
                    System.err.println("❌ Failed to propose to peer " + peer + ": " + e.getMessage());
                }
            }

            // Step 5: Majority check and commit
            int majority = (totalPeers / 2) + 1;
            if (positiveVotes >= majority) {
                System.out.println("🟢 Majority reached. Committing block to all peers and self...");

                for (String peer : Config.PEER_ADDRESSES) {
                    try {
                        ManagedChannel channel = ManagedChannelBuilder.forTarget(peer)
                                .usePlaintext()
                                .build();
                        BlockChainServiceGrpc.BlockChainServiceBlockingStub stub = BlockChainServiceGrpc.newBlockingStub(channel);

                        BlockChainProto.BlockCommitResponse resp = stub.commitBlock(block);
                        System.out.println("📦 Commit to " + peer + ": " + resp.getStatus());

                        channel.shutdown();
                    } catch (Exception e) {
                        System.err.println("❌ Commit failed on peer " + peer + ": " + e.getMessage());
                    }
                }

                // Commit to local disk
                BlockStorage.saveBlock(block);
            } else {
                System.err.println("❌ Not enough votes. Block rejected. Votes: " + positiveVotes + "/" + totalPeers);
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to propose block: " + e.getMessage());
        }
    }





}
