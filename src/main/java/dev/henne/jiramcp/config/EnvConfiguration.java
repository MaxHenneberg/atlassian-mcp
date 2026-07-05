package dev.henne.jiramcp.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class EnvConfiguration {

    @Bean
    ApplicationRunner requireEnvBackedConfiguration(JiraProperties jiraProperties) {
        return args -> {
            // Forces validation at startup and keeps all runtime configuration env-driven.
        };
    }
}
