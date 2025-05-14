package com.codecatalyst.auditchain.grpc;

import com.codecatalyst.auditchain.proto.common.CommonProto;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class SignatureVerifier {

    private static final Gson gson = new Gson();

    public static boolean verify(CommonProto.FileAudit audit) {
        try {
            // Step 1: Decode PEM public key
            String cleanedPem = audit.getPublicKey()
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] pubKeyBytes = Base64.getDecoder().decode(cleanedPem);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(pubKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // Step 2: Reconstruct audit JSON map in Go's key order
            String jsonString = getAuditInJsonFormat(audit);
            System.out.println("Reconstructed JSON for verification:\n" + jsonString);

            // Step 4: Verify the signature using SHA256withRSA
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(jsonString.getBytes(StandardCharsets.UTF_8));  // ⚠️ Do not hash manually!

            byte[] signatureBytes = Base64.getDecoder().decode(audit.getSignature());
            boolean result = sig.verify(signatureBytes);

            if (!result) {
                System.err.println("❌ Signature mismatch.");
            }

            return result;

        } catch (Exception e) {
            System.err.println("❌ Signature verification failed: " + e.getMessage());
            return false;
        }
    }

    public static String getAuditInJsonFormat(CommonProto.FileAudit audit) {
        Map<String, Object> auditMap = new LinkedHashMap<>();

        auditMap.put("access_type", audit.getAccessType().getNumber());

        Map<String, Object> fileInfo = new LinkedHashMap<>();
        fileInfo.put("file_id", audit.getFileInfo().getFileId());
        fileInfo.put("file_name", audit.getFileInfo().getFileName());
        auditMap.put("file_info", fileInfo);

        auditMap.put("req_id", audit.getReqId());
        auditMap.put("timestamp", audit.getTimestamp());

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("user_id", audit.getUserInfo().getUserId());
        userInfo.put("user_name", audit.getUserInfo().getUserName());
        auditMap.put("user_info", userInfo);

        // Step 3: Serialize JSON (same as Go's json.Marshal with sorted keys)
        String jsonString = gson.toJson(auditMap);
        return jsonString;
    }
}
