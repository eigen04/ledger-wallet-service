package com.eigen04.ledger.unit;

import com.eigen04.ledger.util.HashUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilTest {

    @Test
    @DisplayName("SHA-256 should produce consistent hashes")
    void sha256_shouldProduceConsistentHash() {
        String input = "{\"sourceAccountId\":1,\"destinationAccountId\":2,\"amount\":500}";

        String hash1 = HashUtil.sha256(input);
        String hash2 = HashUtil.sha256(input);

        assertNotNull(hash1);
        assertEquals(64, hash1.length(), "SHA-256 hex string should be 64 chars");
        assertEquals(hash1, hash2, "Same input should always produce the same hash");
    }

    @Test
    @DisplayName("Different inputs should produce different hashes")
    void sha256_differentInputs_shouldProduceDifferentHashes() {
        String input1 = "{\"amount\":500}";
        String input2 = "{\"amount\":501}";

        assertNotEquals(HashUtil.sha256(input1), HashUtil.sha256(input2));
    }

    @Test
    @DisplayName("Empty string should produce valid hash")
    void sha256_emptyString_shouldProduceValidHash() {
        String hash = HashUtil.sha256("");
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }
}
