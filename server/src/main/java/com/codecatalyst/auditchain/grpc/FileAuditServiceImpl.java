package com.codecatalyst.auditchain.grpc;

import com.codecatalyst.auditchain.proto.common.CommonProto;
import com.codecatalyst.auditchain.proto.fileaudit.FileAuditProto;
import com.codecatalyst.auditchain.proto.fileaudit.FileAuditServiceGrpc;
import io.grpc.stub.StreamObserver;

import java.util.Arrays;
import java.util.List;

public class FileAuditServiceImpl extends FileAuditServiceGrpc.FileAuditServiceImplBase {

    private final Mempool mempool = new Mempool();

    private final List<String> peerAddresses = Arrays.asList(
            "localhost:50052",
            "localhost:50053"
    );

    private final WhisperClient whisperClient = new WhisperClient(peerAddresses);

    @Override
    public void submitAudit(CommonProto.FileAudit request, StreamObserver<FileAuditProto.FileAuditResponse> responseObserver) {
        boolean isValid = SignatureVerifier.verify(request);

        FileAuditProto.FileAuditResponse.Builder response = FileAuditProto.FileAuditResponse.newBuilder()
                .setReqId(request.getReqId());

        if (isValid) {
            mempool.add(request);
            response.setStatus("success");

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
