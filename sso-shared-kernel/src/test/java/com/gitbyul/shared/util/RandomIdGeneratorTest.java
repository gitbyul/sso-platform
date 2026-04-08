package com.gitbyul.shared.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RandomIdGeneratorTest {

    @Test
    void generateUuidV7_hasVersion7AndDistinct() {
        RandomIdGenerator gen = new RandomIdGenerator();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            UUID u = gen.generateUuidV7();
            assertThat(u.version()).isEqualTo(7);
            assertThat(seen.add(u)).isTrue();
        }
    }
}
