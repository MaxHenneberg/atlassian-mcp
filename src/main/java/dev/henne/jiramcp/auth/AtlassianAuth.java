package dev.henne.jiramcp.auth;

import dev.henne.jiramcp.config.JiraProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class AtlassianAuth {

    private AtlassianAuth() {
    }

    public static String authorizationHeader(JiraProperties properties) {
        return switch (properties.authMode()) {
            case "bearer" -> "Bearer " + properties.pat();
            case "basic" -> {
                String credentials = properties.email() + ":" + properties.pat();
                String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                yield "Basic " + encoded;
            }
            default -> throw new IllegalArgumentException("Unsupported Jira auth mode: " + properties.authMode());
        };
    }
}
