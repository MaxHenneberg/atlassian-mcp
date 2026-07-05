package dev.henne.jiramcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.henne.jiramcp.confluence.ConfluenceClient;
import org.springframework.stereotype.Component;

@Component
public class ConfluenceTools {

    private final ConfluenceClient confluenceClient;

    public ConfluenceTools(ConfluenceClient confluenceClient) {
        this.confluenceClient = confluenceClient;
    }

    public JsonNode listSpaces(int limit) {
        if (limit < 1 || limit > 250) {
            throw new IllegalArgumentException("limit must be between 1 and 250");
        }
        return confluenceClient.listSpaces(limit);
    }

    public JsonNode search(String cql, int limit) {
        if (cql == null || cql.isBlank()) {
            throw new IllegalArgumentException("cql is required");
        }
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit must be between 1 and 50");
        }
        return confluenceClient.search(cql.trim(), limit);
    }
}
