package com.example.apia;

import com.example.common.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.example")
@EnableScheduling
@EntityScan(basePackages = "com.example.common.domain")
@EnableJpaRepositories(basePackages = "com.example.common.repository")
@EnableConfigurationProperties(JwtProperties.class)
public class ApiAApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiAApplication.class, args);
    }
}
