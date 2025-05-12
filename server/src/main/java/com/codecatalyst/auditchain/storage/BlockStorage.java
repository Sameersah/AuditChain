package com.codecatalyst.auditchain.storage;

import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.Block;
import com.google.protobuf.util.JsonFormat;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

public class BlockStorage {
    private static final String BLOCKS_DIR = "data/blocks";

    public static boolean saveBlock(Block block) {
        try {
            // Ensure directory exists
            File dir = new File(BLOCKS_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Write block to JSON file
            String fileName = String.format("block_%d.json", block.getId());
            Path filePath = Path.of(BLOCKS_DIR, fileName);

            String json = JsonFormat.printer().includingDefaultValueFields().print(block);
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(json);
            }

            System.out.println("✅ Block committed to disk: " + filePath);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Failed to save block: " + e.getMessage());
            return false;
        }
    }

    public static boolean blockExists(long blockId) {
        Path path = Path.of(BLOCKS_DIR, String.format("block_%d.json", blockId));
        return Files.exists(path);
    }

    public static Block loadBlock(long blockId) throws Exception {
        Path filePath = Path.of(BLOCKS_DIR, String.format("block_%d.json", blockId));
        if (!filePath.toFile().exists()) {
            throw new Exception("Block file not found");
        }

        try (FileReader reader = new FileReader(filePath.toFile())) {
            Block.Builder builder = Block.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(reader, builder);
            return builder.build();
        }
    }

    public static int getNextBlockId() {
        File dir = new File(BLOCKS_DIR);
        if (!dir.exists() || dir.listFiles() == null) return 0;

        return (int) Arrays.stream(dir.listFiles())
                .filter(file -> file.getName().matches("block_\\d+\\.json"))
                .count();
    }

    public static String getLastBlockHash() {
        File dir = new File(BLOCKS_DIR);
        if (!dir.exists() || dir.listFiles() == null) return "";

        File[] blockFiles = dir.listFiles((d, name) -> name.matches("block_\\d+\\.json"));
        if (blockFiles == null || blockFiles.length == 0) return "";

        File latest = Arrays.stream(blockFiles)
                .max(Comparator.comparingInt(file -> {
                    String name = file.getName().replace("block_", "").replace(".json", "");
                    return Integer.parseInt(name);
                }))
                .orElse(null);

        if (latest == null) return "";

        try (FileReader reader = new FileReader(latest)) {
            Block.Builder builder = Block.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(reader, builder);
            return builder.getHash();
        } catch (Exception e) {
            return "";
        }
    }
}
