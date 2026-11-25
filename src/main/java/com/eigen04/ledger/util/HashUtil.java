package com.eigen04.ledger.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility class for computing SHA-256 hashes of request payloads.
 * Used by the idempotency layer to detect payload mismatches.
 */
public final class HashUtil {

    private HashUtil() {
        // Utility class — no instantiation
    }

    /**
     * Compute SHA-256 hash of a given input string.
     *
     * @param input the string to hash
     * @return hex-encoded SHA-256 hash
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
