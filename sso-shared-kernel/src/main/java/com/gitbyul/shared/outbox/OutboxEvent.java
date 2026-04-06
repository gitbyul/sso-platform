package com.gitbyul.shared.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.gitbyul.shared.util.RandomIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transactional Outbox용 이벤트 엔티티.
 * <p>
 * DB 스키마: {@code shared.outbox_events}
 */
@Entity
@Table(schema = "shared", name = "outbox_events")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
        // JPA
    }

    private OutboxEvent(UUID id,
                         String aggregateType,
                         String aggregateId,
                         String eventType,
                         JsonNode payload) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.published = false;
    }

    @PrePersist
    void onPrePersist() {
        if (this.id == null) {
            this.id = new RandomIdGenerator().generateUuidV7();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public static OutboxEvent create(String aggregateType,
                                      String aggregateId,
                                      String eventType,
                                      JsonNode payload) {
        return new OutboxEvent(
                new RandomIdGenerator().generateUuidV7(),
                aggregateType,
                aggregateId,
                eventType,
                payload
        );
    }

    public void markPublished(Instant publishedAt) {
        this.published = true;
        this.publishedAt = publishedAt;
    }

    public UUID id() {
        return id;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public String eventType() {
        return eventType;
    }

    public JsonNode payload() {
        return payload;
    }

    public boolean published() {
        return published;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant publishedAt() {
        return publishedAt;
    }
}

