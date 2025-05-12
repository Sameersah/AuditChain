package com.codecatalyst.auditchain.grpc;

import com.codecatalyst.auditchain.proto.common.CommonProto;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class SignatureVerifier {

    public static boolean verify(CommonProto.FileAudit audit) {
        try {
            // Clean the PEM string to extract only the base64-encoded key
            String cleanedPem = audit.getPublicKey()
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", ""); // remove all whitespace including newlines

            // Decode the public key
            byte[] pubKeyBytes = Base64.getDecoder().decode(cleanedPem);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(pubKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // Reconstruct the original data string that was signed
            String data = audit.getReqId()
                    + audit.getFileInfo().getFileId()
                    + audit.getFileInfo().getFileName()
                    + audit.getUserInfo().getUserId()
                    + audit.getUserInfo().getUserName()
                    + audit.getAccessType().name()
                    + audit.getTimestamp();

            // Prepare signature verifier
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(data.getBytes());

            // Decode and verify the signature
            byte[] signatureBytes = Base64.getDecoder().decode(audit.getSignature());
            return sig.verify(signatureBytes);

        } catch (Exception e) {
            System.err.println("Signature verification failed: " + e.getMessage());
            return false;
        }
    }
}
