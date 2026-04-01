plugins {
	java
}

group = "com.gitbyul"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {}

tasks.withType<Test> {
	useJUnitPlatform()
}
