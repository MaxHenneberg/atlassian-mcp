package dev.henne.jiramcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpToolExposureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void loadsExplicitJiraAndConfluenceTools() throws IOException {
        Path configFile = writeConfig("""
                {
                  "jira": {
                    "tools": ["jira_get_issue"]
                  },
                  "confluence": {
                    "tools": ["confluence_search"]
                  }
                }
                """);

        McpToolExposure exposure = new McpToolExposure(objectMapper, configFile.toString());

        assertThat(exposure.exposedTools()).containsExactly("jira_get_issue", "confluence_search");
        assertThat(exposure.isExposed("jira_add_comment")).isFalse();
    }

    @Test
    void rejectsConfluenceToolInJiraSection() throws IOException {
        Path configFile = writeConfig("""
                {
                  "jira": {
                    "tools": ["confluence_search"]
                  }
                }
                """);

        assertThatThrownBy(() -> new McpToolExposure(objectMapper, configFile.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tool is not a known jira tool");
    }

    @Test
    void rejectsMissingConfigFile() {
        Path configFile = tempDir.resolve("missing.json");

        assertThatThrownBy(() -> new McpToolExposure(objectMapper, configFile.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MCP tools config file does not exist");
    }

    private Path writeConfig(String json) throws IOException {
        Path configFile = tempDir.resolve("mcp-tools.json");
        Files.writeString(configFile, json);
        return configFile;
    }
}
