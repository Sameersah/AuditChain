package com.codecatalyst.auditchain.util;

import com.codecatalyst.auditchain.proto.common.CommonProto;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

public class HashUtil {

    public static String computeBlockHash(int blockId, String prevHash, List<CommonProto.FileAudit> audits, String merkleRoot) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(blockId).append(prevHash).append(merkleRoot);
            for (CommonProto.FileAudit audit : audits) {
                sb.append(audit.toString());
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(sb.toString().getBytes());
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            return "";
        }
    }
}
