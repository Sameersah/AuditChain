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

import com.codecatalyst.auditchain.election.LeaderStateManager;

import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.TriggerElectionRequest;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.TriggerElectionResponse;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.NotifyLeadershipRequest;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.NotifyLeadershipResponse;





import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class BlockChainServiceImpl extends BlockChainServiceGrpc.BlockChainServiceImplBase {

    private final Mempool mempool = new Mempool();
    private volatile String currentLeader = null;
    private final Map<String, Long> peerHeartbeats = new ConcurrentHashMap<>();
    private final long HEARTBEAT_TIMEOUT_MS = 15000; // 15s timeout
    private final String selfAddress = Config.SELF_ADDRESS; // define in config


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

        boolean allValid = true;
        String errorMessage = "";

        // ✅ Step 1: Verify all audit signatures
        for (CommonProto.FileAudit audit : request.getAuditsList()) {
            if (!SignatureVerifier.verify(audit)) {
                allValid = false;
                errorMessage = "One or more audit signatures failed verification";
                break;
            }
        }

        // ✅ Step 2: Verify previous block hash
        if (allValid) {
            String expectedPreviousHash = BlockStorage.getLastBlockHash();
            System.out.println("Local Previous Block Hash: " + expectedPreviousHash);
            if (!expectedPreviousHash.equals(request.getPreviousHash())) {
                allValid = false;
                errorMessage = "Previous block hash does not match local chain";
            }
        }

        // ✅ Step 3: Verify Merkle root
        if (allValid) {
            String computedMerkleRoot = MerkleUtil.computeMerkleRoot(request.getAuditsList());
            System.out.println("Local Computed Merkle Root: " + computedMerkleRoot);
            if (!computedMerkleRoot.equals(request.getMerkleRoot())) {
                allValid = false;
                errorMessage = "Merkle root mismatch";
            }
        }

        // ✅ Step 4: Verify full block hash
        if (allValid) {
            String computedBlockHash = HashUtil.computeBlockHash(
                    (int) request.getId(),
                    request.getPreviousHash(),
                    request.getAuditsList(),
                    request.getMerkleRoot()
            );

            if (!computedBlockHash.equals(request.getHash())) {
                allValid = false;
                errorMessage = "Block hash mismatch";
            }
        }

        // ✅ Step 5: Respond with vote
        BlockVoteResponse response = BlockVoteResponse.newBuilder()
                .setVote(allValid)
                .setStatus(allValid ? "success" : "failure")
                .setErrorMessage(allValid ? "" : errorMessage)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        System.out.println("🔶 Block proposal vote: " + (allValid ? "APPROVED ✅" : "REJECTED ❌ - " + errorMessage));
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
        System.out.println("Heartbeat received from: " + request.getFromAddress());
        System.out.println("  Leader reported: " + request.getCurrentLeaderAddress());
        System.out.println("  Latest Block ID: " + request.getLatestBlockId());
        System.out.println("  Mempool Size: " + request.getMemPoolSize());

        peerHeartbeats.put(request.getFromAddress(), System.currentTimeMillis());
        request.getCurrentLeaderAddress();
        if (!request.getCurrentLeaderAddress().isEmpty()) {
            currentLeader = request.getCurrentLeaderAddress();
        }

        HeartbeatResponse response = BlockChainProto.HeartbeatResponse.newBuilder()
                .setStatus("success")
                .setErrorMessage("")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }


    @Override
    public void triggerElection(TriggerElectionRequest request, StreamObserver<TriggerElectionResponse> responseObserver) {
        System.out.println("🗳️ Received election request from " + request.getAddress() + " with term " + request.getTerm());

        int localBlockId = BlockStorage.getNextBlockId();
        int incomingTerm = (int) request.getTerm();

        boolean voteGranted = false;
        String errorMsg = "";

        // Grant vote if this term is newer
        if (incomingTerm > LeaderStateManager.getCurrentTerm()) {
            LeaderStateManager.updateTerm(incomingTerm);
            voteGranted = true;
            System.out.println("✅ Vote granted to " + request.getAddress());
        } else {
            errorMsg = "Received term is not higher than current term";
            System.out.println("❌ Vote denied to " + request.getAddress() + ": " + errorMsg);
        }

        TriggerElectionResponse response = TriggerElectionResponse.newBuilder()
                .setVote(voteGranted)
                .setTerm(LeaderStateManager.getCurrentTerm())
                .setStatus("success")
                .setErrorMessage(voteGranted ? "" : errorMsg)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void notifyLeadership(NotifyLeadershipRequest request, StreamObserver<NotifyLeadershipResponse> responseObserver) {
        System.out.println("👑 Leadership notification received: " + request.getAddress());
        LeaderStateManager.setCurrentLeader(request.getAddress());

        NotifyLeadershipResponse response = NotifyLeadershipResponse.newBuilder()
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
            System.out.println("Local Computed Merkle Root: " + merkleRoot);
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

                // ✅ Commit locally and remove audits from local mempool
                boolean saved = BlockStorage.saveBlock(block);
                if (saved) {
                    for (CommonProto.FileAudit audit : block.getAuditsList()) {
                        FileAuditServiceImpl.getMempool().removeAudit(audit.getReqId());
                    }
                    System.out.println("🧹 Removed committed audits from local mempool.");
                }
            } else {
                System.err.println("❌ Not enough votes. Block rejected. Votes: " + positiveVotes + "/" + totalPeers);
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to propose block: " + e.getMessage());
        }
    }


    public void startAutoProposalScheduler() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Check if this node is the current leader
                if (selfAddress.equals(LeaderStateManager.getCurrentLeader())) {
                    // Check if mempool has at least 2 audits
                    if (FileAuditServiceImpl.getMempool().size() >= 2) {
                        System.out.println("🧠 I am the leader and mempool size >= 2. Proposing block...");
                        this.proposeBlockAsLeader();
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Error in auto-proposal scheduler: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS); // Delay 5 sec, run every 5 sec
    }






}
