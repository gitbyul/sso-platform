package com.gitbyul.shared.i18n.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(schema = "shared", name = "localized_messages")
public class LocalizedMessageJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(nullable = false)
    private String locale;

    @Column(name = "message_text", nullable = false)
    private String messageText;

    @Column(name = "tenant_id")
    private String tenantId;

    public UUID getId() {
        return id;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getLocale() {
        return locale;
    }

    public String getMessageText() {
        return messageText;
    }

    public String getTenantId() {
        return tenantId;
    }
}
