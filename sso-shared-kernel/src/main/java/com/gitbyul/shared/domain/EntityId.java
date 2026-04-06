package com.gitbyul.shared.domain;

import com.gitbyul.shared.util.RandomIdGenerator;

import java.util.Objects;
import java.util.UUID;

/**
 * 엔티티 식별자 래퍼 (UUID 기반).
 */
public final class EntityId {

    private final UUID value;

    private EntityId(UUID value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public UUID value() {
        return value;
    }

    public static EntityId of(UUID value) {
        return new EntityId(value);
    }

    public static EntityId generate() {
        return new EntityId(new RandomIdGenerator().generateUuidV7());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EntityId entityId = (EntityId) o;
        return value.equals(entityId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

