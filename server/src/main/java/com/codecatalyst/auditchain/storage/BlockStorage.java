package com.codecatalyst.auditchain.storage;

import com.codecatalyst.auditchain.proto.blockchain.BlockChainProto.Block;
import com.google.protobuf.util.JsonFormat;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
