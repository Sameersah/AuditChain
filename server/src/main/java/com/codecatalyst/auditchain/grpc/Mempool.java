package com.codecatalyst.auditchain.grpc;

import com.codecatalyst.auditchain.proto.common.CommonProto;
import com.google.protobuf.util.JsonFormat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Mempool {
    private static final List<CommonProto.FileAudit> audits = new ArrayList<>();

    public synchronized void add(CommonProto.FileAudit audit) {
        audits.add(audit);
        System.out.println("➕ Added audit to mempool: " + audit.getReqId());
    }

    public static synchronized List<CommonProto.FileAudit> getAll() {
        return new ArrayList<>(audits);
    }

    public synchronized void clear() {
        audits.clear();
        System.out.println("🧹 Mempool cleared");
    }

    public synchronized void removeAudit(String reqId) {
        // Debug: Log the size of the mempool before attempting removal
        System.out.println("🔍 Attempting to remove audit. Current mempool size: " + audits.size());

        // Debug: Log the request ID being searched for
        System.out.println("🔍 Searching for audit with reqId: " + reqId);

        // Create an iterator to traverse the list of audits
        Iterator<CommonProto.FileAudit> it = audits.iterator();

        // Iterate through the mempool to find the audit with the matching reqId
        while (it.hasNext()) {
            CommonProto.FileAudit audit = it.next();

            // Debug: Log the current audit being checked
            System.out.println("🔍 Checking audit with reqId: " + audit.getReqId());

            // Check if the current audit's reqId matches the one to be removed
            if (audit.getReqId().equals(reqId)) {
                // If a match is found, remove the audit from the mempool
                it.remove();

                // Debug: Log successful removal
                System.out.println("🗑️ Successfully removed audit from mempool: " + reqId);

                // Debug: Log the size of the mempool after removal
                System.out.println("🔍 Mempool size after removal: " + audits.size());
                return;
            }
        }

        // If no matching audit is found, log a warning
        System.out.println("⚠️ Audit not found in mempool: " + reqId);

        // Debug: Log the size of the mempool after the search
        System.out.println("🔍 Mempool size after search: " + audits.size());
    }

    public synchronized boolean contains(String reqId) {
        return audits.stream().anyMatch(a -> a.getReqId().equals(reqId));
    }

    public synchronized void printMempool() {
        System.out.println("\n=== Mempool Contents ===");
        System.out.println("Total audits in mempool: " + audits.size());
        System.out.println("------------------------");

        for (CommonProto.FileAudit audit : audits) {
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

    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < audits.size(); i++) {
            try {
                String json = JsonFormat.printer().print(audits.get(i));
                sb.append(json);
                if (i < audits.size() - 1) sb.append(",");
            } catch (Exception e) {
                sb.append("\"error\"");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public synchronized int size() {
        return audits.size();
    }
}
