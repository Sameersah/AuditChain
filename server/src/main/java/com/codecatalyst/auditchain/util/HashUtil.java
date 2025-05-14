package com.codecatalyst.auditchain.util;

import com.codecatalyst.auditchain.proto.common.CommonProto;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static com.codecatalyst.auditchain.grpc.SignatureVerifier.getAuditInJsonFormat;

public class HashUtil {

    public static String computeBlockHash(int blockId, String prevHash, List<CommonProto.FileAudit> audits, String merkleRoot) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(blockId).append(prevHash).append(merkleRoot);

            for (CommonProto.FileAudit audit : audits) {
                sb.append(getAuditInJsonFormat(audit));  // This must match Python's get_audit_json(audit)
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));

            // Convert to hex string (like Python’s hexdigest)
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();  // hex string like Python’s hashlib.sha256().hexdigest()

        } catch (Exception e) {
            return "";
        }
    }
}
