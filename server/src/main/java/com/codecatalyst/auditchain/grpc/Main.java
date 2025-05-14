package com.codecatalyst.auditchain.grpc;

import com.codecatalyst.auditchain.config.Config;
import com.codecatalyst.auditchain.grpc.HeartbeatClient;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.codecatalyst.auditchain.leader.ElectionManager;

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

        blockChainService.startAutoProposalScheduler();
        // Start Spark HTTP server on 9090
        port(9090);

        // Endpoint to manually trigger block proposal
        post("/propose-block", (req, res) -> {
            try {
                blockChainService.proposeBlockAsLeader();
                return "✅ Block proposal triggered";
            } catch (Exception e) {
                e.printStackTrace();
                return "❌ Error: " + e.getMessage();
            }
        });

        // Endpoint to return mempool contents
        get("/mempool", (req, res) -> {
            res.type("application/json");
            try {
                return FileAuditServiceImpl.getMempool().toJson();
            } catch (Exception e) {
                e.printStackTrace();
                return "[]";
            }
        });

        // Schedule heartbeat every 5 seconds
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                HeartbeatClient.broadcastHeartbeat();
            } catch (Exception e) {
                System.err.println("❌ Heartbeat failed: " + e.getMessage());
            }
        }, 1, 10, TimeUnit.SECONDS);


        // Start leader election monitor (runs every 5 seconds)
         ElectionManager.startElectionMonitor(Config.NODE_ID);


        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down server...");
            FileAuditServiceImpl.getMempool().printMempool();
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
