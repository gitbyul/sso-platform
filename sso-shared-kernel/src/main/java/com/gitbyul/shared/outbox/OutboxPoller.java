package com.gitbyul.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitbyul.shared.util.TimeProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transactional Outbox 미발행 레코드를 Redis Streams 로 발행한 뒤 {@code published} 로 표시한다.
 * <p>
 * 스트림 키: {@value #OUTBOX_STREAM} (소비 측에서 eventType 등으로 라우팅).
 */
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    /**
     * 도메인 이벤트를 한 스트림으로 모은 뒤, 컨슈머가 분기 처리한다.
     */
    public static final String OUTBOX_STREAM = "sso:stream:domain-events";

    private final EntityManager entityManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TimeProvider timeProvider;

    public OutboxPoller(EntityManager entityManager,
                        StringRedisTemplate stringRedisTemplate,
                        ObjectMapper objectMapper,
                        TimeProvider timeProvider) {
        this.entityManager = entityManager;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.timeProvider = timeProvider;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void poll() {
        List<OutboxEvent> pending = entityManager.createQuery(
                        "select o from OutboxEvent o where o.published = false order by o.createdAt asc",
                        OutboxEvent.class
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(100)
                .getResultList();

        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pending) {
            try {
                publishToStream(event);
                event.markPublished(timeProvider.now());
            } catch (Exception e) {
                log.warn("Outbox publish failed, will retry id={} eventType={}", event.id(), event.eventType(), e);
            }
        }
    }

    private void publishToStream(OutboxEvent event) throws JsonProcessingException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", event.id().toString());
        fields.put("eventType", event.eventType());
        fields.put("aggregateType", event.aggregateType());
        fields.put("aggregateId", event.aggregateId());
        fields.put("occurredAt", event.createdAt().toString());
        fields.put("payload", payloadAsJson(event.payload()));

        RecordId recordId = stringRedisTemplate.opsForStream().add(
                StreamRecords.mapBacked(fields).withStreamKey(OUTBOX_STREAM)
        );
        if (recordId == null) {
            throw new IllegalStateException("Redis XADD returned null record id");
        }
        log.debug("Outbox published stream={} id={} redisRecordId={}", OUTBOX_STREAM, event.id(), recordId.getValue());
    }

    private String payloadAsJson(JsonNode payload) throws JsonProcessingException {
        if (payload == null) {
            return "{}";
        }
        return objectMapper.writeValueAsString(payload);
    }
}
