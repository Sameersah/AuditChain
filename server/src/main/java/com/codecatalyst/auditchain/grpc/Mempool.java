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
}
