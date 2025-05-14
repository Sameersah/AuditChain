package com.codecatalyst.auditchain.util;

import com.codecatalyst.auditchain.proto.common.CommonProto;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static com.codecatalyst.auditchain.grpc.SignatureVerifier.getAuditInJsonFormat;

public class MerkleUtil {

    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public static String computeMerkleRoot(List<CommonProto.FileAudit> audits) {
        List<String> hashes = new ArrayList<>();
        for (CommonProto.FileAudit audit : audits) {
            String json = getAuditInJsonFormat(audit);
           // System.out.println("Audit JSON: " + json);
            String hash = hashSHA256(json);
            System.out.println("Hash :" + hash);
            hashes.add(hash);
        }

        /*//print initial audit hashes for debugging
        for(int i = 0; i < hashes.size(); i++) {
            System.out.println("Hash " + (i + 1) + ": " + hashes.get(i));
        }*/

        // Build Merkle tree
        while (hashes.size() > 1) {
            List<String> newHashes = new ArrayList<>();
            for (int i = 0; i < hashes.size(); i += 2) {
                String left = hashes.get(i);
                System.out.println("Left: " + left);
                String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left; // duplicate last if odd
                System.out.println("Right: " + right);
                String hash = hashSHA256(left + right);
                System.out.println("Combined Hash: " + hash);
                newHashes.add(hash);
            }
            hashes = newHashes;
        }

        return hashes.isEmpty() ? "" : hashes.get(0);
    }

/*    private static String hashSHA256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8)); // ✅ use UTF-8
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }*/

    public static String hashSHA256(String data) {
        try {
           // input = "{\"access_type\":3,\"file_info\":{\"file_id\":\"f004\",\"file_name\":\"config.json\"},\"req_id\":\"0e5f23c1-e936-42e6-8e25-94ef46dcfc71\",\"timestamp\":1747105079,\"user_info\":{\"user_id\":\"u003\",\"user_name\":\"charlie\"}}";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(data.getBytes(StandardCharsets.UTF_8));
            BigInteger bigInt = new BigInteger(1, hashBytes);
            StringBuilder hexString = new StringBuilder(bigInt.toString(16));
            while (hexString.length() < 64) {
                hexString.insert(0, '0');
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

/*    public static String getAuditInJsonFormat(CommonProto.FileAudit audit) {
        Map<String, Object> auditMap = new LinkedHashMap<>();

        // Maintain Python's audit structure
        auditMap.put("req_id", audit.getReqId());

        Map<String, Object> fileInfo = new LinkedHashMap<>();
        fileInfo.put("file_id", audit.getFileInfo().getFileId());
        fileInfo.put("file_name", audit.getFileInfo().getFileName());
        auditMap.put("file_info", fileInfo);

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("user_id", audit.getUserInfo().getUserId());
        userInfo.put("user_name", audit.getUserInfo().getUserName());
        auditMap.put("user_info", userInfo);

        auditMap.put("access_type", audit.getAccessType().getNumber()); // as int
        auditMap.put("timestamp", audit.getTimestamp());

        return gson.toJson(auditMap);
    }*/
}
