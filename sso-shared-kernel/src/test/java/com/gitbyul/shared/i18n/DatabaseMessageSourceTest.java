package com.gitbyul.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.gitbyul.shared.tenant.TenantContextHolder;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.StaticMessageSource;

class DatabaseMessageSourceTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void usesTenantContextWhenResolving() {
        LocalizedMessageRepository repo = Mockito.mock(LocalizedMessageRepository.class);
        when(repo.findMessage(eq("error.X"), eq(Locale.KOREAN), eq("acme")))
                .thenReturn(Optional.of("한글"));
        when(repo.findMessage(eq("error.X"), eq(Locale.KOREAN), eq(null)))
                .thenReturn(Optional.of("플랫폼"));

        DatabaseMessageSource source = new DatabaseMessageSource(repo);
        StaticMessageSource parent = new StaticMessageSource();
        parent.addMessage("error.X", Locale.ENGLISH, "en-fallback");
        source.setParentMessageSource(parent);

        TenantContextHolder.set("acme");
        assertThat(source.getMessage("error.X", null, Locale.KOREAN)).isEqualTo("한글");

        TenantContextHolder.clear();
        assertThat(source.getMessage("error.X", null, Locale.KOREAN)).isEqualTo("플랫폼");
    }

    @Test
    void fallsBackToParentWhenDbMissing() {
        LocalizedMessageRepository repo = Mockito.mock(LocalizedMessageRepository.class);
        when(repo.findMessage(any(), any(), any())).thenReturn(Optional.empty());

        DatabaseMessageSource source = new DatabaseMessageSource(repo);
        StaticMessageSource parent = new StaticMessageSource();
        parent.addMessage("error.Y", Locale.ENGLISH, "from-parent");
        source.setParentMessageSource(parent);

        assertThat(source.getMessage("error.Y", null, Locale.ENGLISH)).isEqualTo("from-parent");
    }
}
