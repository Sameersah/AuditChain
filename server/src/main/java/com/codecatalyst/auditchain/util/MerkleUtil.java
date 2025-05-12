package com.codecatalyst.auditchain.util;

import com.codecatalyst.auditchain.proto.common.CommonProto;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class MerkleUtil {

    public static String computeMerkleRoot(List<CommonProto.FileAudit> audits) {
        List<String> hashes = new ArrayList<>();
        for (CommonProto.FileAudit audit : audits) {
            hashes.add(hashSHA256(audit.toString()));
        }

        while (hashes.size() > 1) {
            List<String> newHashes = new ArrayList<>();
            for (int i = 0; i < hashes.size(); i += 2) {
                String left = hashes.get(i);
                String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left;
                newHashes.add(hashSHA256(left + right));
            }
            hashes = newHashes;
        }

        return hashes.isEmpty() ? "" : hashes.get(0);
    }

    private static String hashSHA256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(data.getBytes()));
        } catch (Exception e) {
            return "";
        }
    }
}
