package com.gitbyul.sso_bootstrap;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackSpringConfigurationTest {

    @Test
    void logbackSpringXmlIncludesDockerStructuredConsoleAppenders() throws Exception {
        try (InputStream in =
                Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream("logback-spring.xml")) {
            assertThat(in).isNotNull();
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml).contains("docker");
            assertThat(xml).contains("structured-console-appender.xml");
        }
    }
}
