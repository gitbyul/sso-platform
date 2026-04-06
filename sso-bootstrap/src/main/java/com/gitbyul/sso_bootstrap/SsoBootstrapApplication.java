package com.gitbyul.sso_bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = "com.gitbyul")
public class SsoBootstrapApplication {

	public static void main(String[] args) {
		SpringApplication.run(SsoBootstrapApplication.class, args);
	}

}
