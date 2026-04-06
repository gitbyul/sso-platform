package com.gitbyul.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitbyul.shared.outbox.OutboxPoller;
import com.gitbyul.shared.tenant.MdcPropagationFilter;
import com.gitbyul.shared.tenant.TenantJwtValidationFilter;
import com.gitbyul.shared.tenant.TenantResolutionFilter;
import com.gitbyul.shared.util.RandomIdGenerator;
import com.gitbyul.shared.util.SystemTimeProvider;
import com.gitbyul.shared.util.TimeProvider;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * shared-kernel 필터·유틸·Outbox 폴러 자동 구성.
 * <p>
 * 이 모듈 JAR의 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} 로 로딩된다.
 */
@AutoConfiguration
@ConditionalOnClass(name = "jakarta.servlet.Servlet")
@EnableScheduling
public class SharedKernelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TimeProvider timeProvider() {
        return new SystemTimeProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public RandomIdGenerator randomIdGenerator() {
        return new RandomIdGenerator();
    }

    @Bean
    public TenantResolutionFilter tenantResolutionFilter(Environment environment, ObjectMapper objectMapper) {
        return new TenantResolutionFilter(environment, objectMapper);
    }

    @Bean
    public MdcPropagationFilter mdcPropagationFilter(ObjectMapper objectMapper) {
        return new MdcPropagationFilter(objectMapper);
    }

    @Bean
    public TenantJwtValidationFilter tenantJwtValidationFilter(
            ApplicationEventPublisher publisher,
            ObjectMapper objectMapper,
            RandomIdGenerator randomIdGenerator,
            TimeProvider timeProvider) {
        return new TenantJwtValidationFilter(publisher, objectMapper, randomIdGenerator, timeProvider);
    }

    @Bean
    @ConditionalOnBean({EntityManager.class, StringRedisTemplate.class})
    public OutboxPoller outboxPoller(
            EntityManager entityManager,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            TimeProvider timeProvider) {
        return new OutboxPoller(entityManager, stringRedisTemplate, objectMapper, timeProvider);
    }
}
