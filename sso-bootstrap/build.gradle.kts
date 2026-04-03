plugins {
	java
	id("org.springframework.boot") version "4.0.5"
	id("io.spring.dependency-management") version "1.1.7"
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

dependencies {
	implementation(project(":sso-shared-kernel"))
	implementation(project(":sso-identity-context"))
	implementation(project(":sso-tenant-context"))
	implementation(project(":sso-client-context"))
	implementation(project(":sso-authorization-context"))
	implementation(project(":sso-federation-context"))
	implementation(project(":sso-session-context"))
	implementation(project(":sso-key-context"))
	implementation(project(":sso-audit-context"))
	implementation(project(":sso-admin-context"))
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.bootRun {
	systemProperty("spring.profiles.active", "local")
}
