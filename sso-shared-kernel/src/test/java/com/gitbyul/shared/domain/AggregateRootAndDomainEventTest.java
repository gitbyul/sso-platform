package com.gitbyul.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregateRootAndDomainEventTest {

    @Test
    void raiseEvent_accumulatesAndClears() {
        TestAggregate agg = new TestAggregate();
        assertThat(agg.hasDomainEvents()).isFalse();

        TestDomainEvent e = new TestDomainEvent(UUID.randomUUID());
        agg.record(e);

        assertThat(agg.domainEvents()).containsExactly(e);
        assertThat(agg.hasDomainEvents()).isTrue();

        agg.clearDomainEvents();
        assertThat(agg.hasDomainEvents()).isFalse();
    }

    @Test
    void raiseEvent_rejectsNull() {
        TestAggregate agg = new TestAggregate();
        assertThatThrownBy(() -> agg.record(null)).isInstanceOf(NullPointerException.class);
    }

    private static final class TestAggregate extends AggregateRoot {
        void record(TestDomainEvent event) {
            raiseEvent(event);
        }
    }

    private record TestDomainEvent(UUID eventId) implements DomainEvent {}
}
