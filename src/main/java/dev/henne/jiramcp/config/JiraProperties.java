package dev.henne.jiramcp.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jira")
public record JiraProperties(
        @NotBlank String baseUrl,
        @NotBlank String pat,
        String email,
        String authMode,
        String apiVersion
) {

    public JiraProperties {
        authMode = (authMode == null || authMode.isBlank()) ? "bearer" : authMode.toLowerCase();
        apiVersion = (apiVersion == null || apiVersion.isBlank()) ? "3" : apiVersion;
        baseUrl = trimTrailingSlash(baseUrl);
        if ("basic".equals(authMode) && (email == null || email.isBlank())) {
            throw new IllegalArgumentException("jira.email is required when jira.auth-mode=basic");
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
