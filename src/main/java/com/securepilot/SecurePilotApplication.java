package com.securepilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SecurePilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurePilotApplication.class, args);
    }
}
