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

        // UUID v7 (RFC 9562): MSW에 version·rand_a, LSW 상위 2비트에 variant(10), 하위 62비트에 rand_b 전부.
        long mostSignificantBits = (timestampMs << 16) | (0x7L << 12) | randomA;
        long leastSignificantBits = (2L << 62) | randomB;

        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}

