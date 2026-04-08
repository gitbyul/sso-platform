package com.gitbyul.sso_bootstrap;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackSpringConfigurationTest {

    @Test
    void dockerProfileIncludesStructuredConsoleAppenderResource() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);

        try (InputStream in =
                Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream("logback-spring.xml")) {
            assertThat(in).isNotNull();
            Document doc = factory.newDocumentBuilder().parse(in);

            NodeList profiles = doc.getElementsByTagName("springProfile");
            assertThat(profiles.getLength()).isPositive();

            boolean dockerProfileIncludesStructuredConsole = false;
            for (int i = 0; i < profiles.getLength(); i++) {
                Element profile = (Element) profiles.item(i);
                String name = profile.getAttribute("name");
                if (name == null || !name.contains("docker")) {
                    continue;
                }
                NodeList includes = profile.getElementsByTagName("include");
                for (int j = 0; j < includes.getLength(); j++) {
                    Element include = (Element) includes.item(j);
                    String resource = include.getAttribute("resource");
                    if ("org/springframework/boot/logging/logback/structured-console-appender.xml"
                            .equals(resource)) {
                        dockerProfileIncludesStructuredConsole = true;
                        break;
                    }
                }
            }

            assertThat(dockerProfileIncludesStructuredConsole)
                    .as("docker(및 동일 블록) 프로파일에서 구조화 콘솔 appender include")
                    .isTrue();
        }
    }
}
