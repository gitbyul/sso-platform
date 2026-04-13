package com.gitbyul.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityIdTest {

    @Test
    void of_and_equals_hashCode() {
        UUID u = UUID.randomUUID();
        EntityId a = EntityId.of(u);
        EntityId b = EntityId.of(u);
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a.value()).isEqualTo(u);
    }

    @Test
    void generate_usesUuidV7Semantics() {
        EntityId id = EntityId.generate();
        UUID u = id.value();
        assertThat(u.version()).isEqualTo(7);
        assertThat(u.variant()).isEqualTo(2);
    }

    @Test
    void of_rejectsNull() {
        assertThatThrownBy(() -> EntityId.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void two_generates_areDistinct() {
        assertThat(EntityId.generate()).isNotEqualTo(EntityId.generate());
    }
}
