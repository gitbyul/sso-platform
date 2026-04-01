plugins {
	java
	id("io.spring.dependency-management") version "1.1.7" apply false
	id("org.springframework.boot") version "4.0.5" apply false
}

group = "com.gitbyul"
version = "0.0.1-SNAPSHOT"

subprojects {
	apply(plugin = "java")
	apply(plugin = "io.spring.dependency-management")

	java {
		toolchain {
			languageVersion = JavaLanguageVersion.of(25)
		}
	}

	repositories {
		mavenCentral()
	}

	tasks.withType<Test> {
		useJUnitPlatform()
	}
}
