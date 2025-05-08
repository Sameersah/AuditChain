package com.codecatalyst.auditchain.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public class Main {
    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder
                .forPort(50052)
                .addService(new FileAuditServiceImpl())
                .addService(new BlockChainServiceImpl())
                .build();

        server.start();
        System.out.println("gRPC Server started on port 50052");
        server.awaitTermination();
    }
}
