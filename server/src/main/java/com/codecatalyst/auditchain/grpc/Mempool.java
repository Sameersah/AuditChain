package com.codecatalyst.auditchain.grpc;

import com.codecatalyst.auditchain.proto.common.CommonProto;

import java.util.ArrayList;
import java.util.List;

public class Mempool {
    private final List<CommonProto.FileAudit> audits = new ArrayList<>();

    public synchronized void add(CommonProto.FileAudit audit) {
        audits.add(audit);
        System.out.println("Added audit to mempool: " + audit.getReqId());
    }

    public synchronized List<CommonProto.FileAudit> getAll() {
        return new ArrayList<>(audits);
    }

    public synchronized void clear() {
        audits.clear();
    }

    public synchronized void printMempool() {
        System.out.println("\n=== Mempool Contents ===");
        System.out.println("Total audits in mempool: " + audits.size());
        System.out.println("------------------------");
        
        for (CommonProto.FileAudit audit : audits) {
            System.out.println("\nAudit Details:");
            System.out.println("Request ID: " + audit.getReqId());
            System.out.println("File ID: " + audit.getFileInfo().getFileId());
            System.out.println("File Name: " + audit.getFileInfo().getFileName());
            System.out.println("User ID: " + audit.getUserInfo().getUserId());
            System.out.println("User Name: " + audit.getUserInfo().getUserName());
            System.out.println("Access Type: " + audit.getAccessType());
            System.out.println("Timestamp: " + audit.getTimestamp());
            System.out.println("------------------------");
        }
        System.out.println("=== End of Mempool ===\n");
    }
}
