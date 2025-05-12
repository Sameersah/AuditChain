package com.codecatalyst.auditchain.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import static spark.Spark.*;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 50052;

        // Instantiate service implementations
        BlockChainServiceImpl blockChainService = new BlockChainServiceImpl();
        FileAuditServiceImpl auditService = new FileAuditServiceImpl();

        // Start gRPC server
        Server server = ServerBuilder
                .forPort(port)
                .addService(auditService)
                .addService(blockChainService)
                .build();

        server.start();
        System.out.println("🚀 gRPC Server started on port " + port);

        // ✅ Start lightweight Spark HTTP server
        port(9090); // You can change this if needed

        post("/propose-block", (req, res) -> {
            try {
                blockChainService.proposeBlockAsLeader();
                return "✅ Block proposal triggered";
            } catch (Exception e) {
                e.printStackTrace();
                return "❌ Error: " + e.getMessage();
            }
        });

        get("/mempool", (req, res) -> {
            res.type("application/json");
            try {
                return FileAuditServiceImpl.getMempool().toJson();
            } catch (Exception e) {
                e.printStackTrace();
                return "[]";
            }
        });

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down server...");
            FileAuditServiceImpl.getMempool().printMempool();
        }));

        server.awaitTermination();
    }
}
