package com.gitbyul.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.gitbyul.shared.i18n.jpa.JpaLocalizedMessageRepository;
import com.gitbyul.shared.i18n.jpa.LocalizedMessageJpaEntity;
import com.gitbyul.shared.i18n.jpa.LocalizedMessageJpaRepository;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class JpaLocalizedMessageRepositoryTest {

    @Test
    void prefersTenantRowOverPlatform() {
        LocalizedMessageJpaRepository jpa = Mockito.mock(LocalizedMessageJpaRepository.class);
        LocalizedMessageJpaEntity tenantRow = entity("tenant-msg");
        LocalizedMessageJpaEntity platformRow = entity("platform-msg");
        when(jpa.findByTenantIdAndMessageKeyAndLocale(eq("t1"), eq("error.X"), eq("ko")))
                .thenReturn(Optional.of(tenantRow));
        when(jpa.findByMessageKeyAndLocaleAndTenantIdIsNull(eq("error.X"), eq("ko")))
                .thenReturn(Optional.of(platformRow));

        JpaLocalizedMessageRepository repo = new JpaLocalizedMessageRepository(jpa);
        assertThat(repo.findMessage("error.X", Locale.KOREAN, "t1")).contains("tenant-msg");
    }

    @Test
    void fallsBackToPlatformWhenTenantMissing() {
        LocalizedMessageJpaRepository jpa = Mockito.mock(LocalizedMessageJpaRepository.class);
        LocalizedMessageJpaEntity platformRow = entity("platform-msg");
        when(jpa.findByTenantIdAndMessageKeyAndLocale(eq("t1"), eq("error.X"), eq("ko")))
                .thenReturn(Optional.empty());
        when(jpa.findByMessageKeyAndLocaleAndTenantIdIsNull(eq("error.X"), eq("ko")))
                .thenReturn(Optional.of(platformRow));

        JpaLocalizedMessageRepository repo = new JpaLocalizedMessageRepository(jpa);
        assertThat(repo.findMessage("error.X", Locale.KOREAN, "t1")).contains("platform-msg");
    }

    private static LocalizedMessageJpaEntity entity(String text) {
        LocalizedMessageJpaEntity e = Mockito.mock(LocalizedMessageJpaEntity.class);
        when(e.getMessageText()).thenReturn(text);
        return e;
    }
}
