package com.my.jwt;

import org.springframework.boot.SpringApplication; // Bootstraps the Spring application context
import org.springframework.boot.autoconfigure.SpringBootApplication; // Enables component scan, auto-config, etc.
import org.springframework.boot.context.properties.ConfigurationPropertiesScan; // Scans for @ConfigurationProperties beans

@SpringBootApplication // Enables @ComponentScan, @EnableAutoConfiguration, and @Configuration
@ConfigurationPropertiesScan // Registers all @ConfigurationProperties beans in com.my.jwt.*
public class JwtAuthApplication {

	public static void main(String[] args) {
		SpringApplication.run(JwtAuthApplication.class, args);
	}

}
