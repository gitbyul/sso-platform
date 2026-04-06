package com.gitbyul.shared.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 시간순 정렬이 가능한 UUID v7 생성기.
 */
public final class RandomIdGenerator {

    private static final long RANDOM_A_MASK = (1L << 12) - 1; // 12 bits
    private static final long RANDOM_B_MASK = (1L << 62) - 1; // 62 bits

    public UUID generateUuidV7() {
        long timestampMs = System.currentTimeMillis() & 0xFFFFFFFFFFFFL; // 48 bits

        long randomA = ThreadLocalRandom.current().nextLong() & RANDOM_A_MASK; // 12 bits
        long randomB = ThreadLocalRandom.current().nextLong() & RANDOM_B_MASK; // 62 bits

        // UUID v7:
        // - version: 4 bits (0111)
        // - variant: 2 bits (10)
        long mostSignificantBits = (timestampMs << 16) | (0x7L << 12) | randomA;
        long leastSignificantBits = (randomB << 2) | 0x2L;

        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}

