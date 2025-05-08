package com.codecatalyst.auditchain.grpc;

import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto;
import com.codecatalyst.auditchain.proto.blockchain.BlockChainServiceGrpc;
import com.codecatalyst.auditchain.proto.common.CommonProto;
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
}
