package dev.henne.jiramcp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.henne.jiramcp.mcp.McpToolCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class McpToolExposure {

    private static final Logger log = LoggerFactory.getLogger(McpToolExposure.class);

    private final Set<String> exposedTools;

    public McpToolExposure(
            ObjectMapper objectMapper,
            @Value("${mcp.tools.config-file}") String configFile
    ) {
        this.exposedTools = Collections.unmodifiableSet(loadExposedTools(objectMapper, configFile));
        log.info("Loaded MCP tool exposure config file={} exposedTools={}", configFile, exposedTools);
    }

    public boolean isExposed(String toolName) {
        return exposedTools.contains(toolName);
    }

    public Set<String> exposedTools() {
        return exposedTools;
    }

    private Set<String> loadExposedTools(ObjectMapper objectMapper, String configFile) {
        if (configFile == null || configFile.isBlank()) {
            throw new IllegalStateException("mcp.tools.config-file must point to a JSON config file");
        }

        Path configPath = Path.of(configFile);
        if (!Files.isRegularFile(configPath)) {
            throw new IllegalStateException("MCP tools config file does not exist: " + configPath.toAbsolutePath());
        }

        JsonNode root;
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            root = objectMapper.readTree(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read MCP tools config file: " + configPath.toAbsolutePath(), exception);
        }

        if (root == null || !root.isObject()) {
            throw new IllegalStateException("MCP tools config must be a JSON object");
        }

        rejectUnknownDomains(root);

        Set<String> exposed = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> domain : McpToolCatalog.TOOLS_BY_DOMAIN.entrySet()) {
            readDomainTools(root, domain.getKey(), domain.getValue(), exposed);
        }
        return exposed;
    }

    private void rejectUnknownDomains(JsonNode root) {
        Set<String> knownDomains = McpToolCatalog.TOOLS_BY_DOMAIN.keySet();
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!knownDomains.contains(fieldName)) {
                throw new IllegalStateException("Unknown MCP tools config section: " + fieldName
                        + ". Allowed sections: " + String.join(", ", knownDomains));
            }
        }
    }

    private void readDomainTools(JsonNode root, String domainName, List<String> knownTools, Set<String> exposed) {
        JsonNode domain = root.path(domainName);
        if (domain.isMissingNode() || domain.isNull()) {
            return;
        }
        if (!domain.isObject()) {
            throw new IllegalStateException("MCP tools config section must be an object: " + domainName);
        }

        Iterator<String> fieldNames = domain.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!"tools".equals(fieldName)) {
                throw new IllegalStateException("Unknown MCP tools config field: " + domainName + "." + fieldName
                        + ". Allowed field: tools");
            }
        }

        JsonNode tools = domain.path("tools");
        if (tools.isMissingNode() || tools.isNull()) {
            throw new IllegalStateException("MCP tools config section must define an explicit tools array: " + domainName);
        }
        if (!tools.isArray()) {
            throw new IllegalStateException("MCP tools config field must be an array: " + domainName + ".tools");
        }

        Set<String> seenInDomain = new HashSet<>();
        for (JsonNode tool : tools) {
            if (!tool.isTextual() || tool.asText().isBlank()) {
                throw new IllegalStateException("MCP tools config entries must be non-empty strings: " + domainName + ".tools");
            }

            String toolName = tool.asText();
            if (!knownTools.contains(toolName)) {
                throw new IllegalStateException("Tool is not a known " + domainName + " tool: " + toolName
                        + ". Allowed " + domainName + " tools: " + String.join(", ", knownTools));
            }
            if (!seenInDomain.add(toolName)) {
                throw new IllegalStateException("Duplicate MCP tool in config: " + domainName + "." + toolName);
            }
            exposed.add(toolName);
        }
    }
}
