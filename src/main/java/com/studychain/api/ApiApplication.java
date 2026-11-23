package com.studychain.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point. @SpringBootApplication makes Spring scan this package and everything below it,
 * so every controller, service and repository under com.studychain.api is found automatically.
 * @ConfigurationPropertiesScan registers AppProperties (the "studychain.*" settings).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
