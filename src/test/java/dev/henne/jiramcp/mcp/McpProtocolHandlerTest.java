package dev.henne.jiramcp.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.henne.jiramcp.tools.ConfluenceTools;
import dev.henne.jiramcp.tools.JiraTools;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpProtocolHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JiraTools jiraTools = mock(JiraTools.class);
    private final ConfluenceTools confluenceTools = mock(ConfluenceTools.class);
    private final McpProtocolHandler handler = new McpProtocolHandler(objectMapper, jiraTools, confluenceTools);

    @Test
    void listsJiraGetIssueTool() {
        ObjectNode request = request(1, "tools/list");

        var response = handler.handle(request);

        assertThat(response.path("result").path("tools").get(0).path("name").asText()).isEqualTo("jira_get_issue");
        assertThat(response.path("result").path("tools").get(0).path("inputSchema").path("required").get(0).asText())
                .isEqualTo("issueKey");
        assertThat(response.path("result").path("tools").get(0).path("description").asText())
                .contains("Request the smallest safe field set", "If fields is omitted or empty, no Jira fields are exported");
        assertThat(response.path("result").path("tools").findValuesAsText("name"))
                .contains("jira_add_comment", "jira_transition_issue", "confluence_list_spaces", "confluence_search");
    }

    @Test
    void callsJiraGetIssueTool() {
        ObjectNode issue = objectMapper.createObjectNode();
        issue.put("key", "PROJ-123");
        when(jiraTools.getIssue("PROJ-123", List.of("summary", "status"))).thenReturn(issue);

        ObjectNode request = request(2, "tools/call");
        ObjectNode params = request.putObject("params");
        params.put("name", "jira_get_issue");
        params.putObject("arguments")
                .put("issueKey", "PROJ-123")
                .putArray("fields")
                .add("summary")
                .add("status");

        var response = handler.handle(request);

        assertThat(response.path("result").path("isError").asBoolean()).isFalse();
        assertThat(response.path("result").path("content").get(0).path("text").asText()).contains("\"key\" : \"PROJ-123\"");
    }

    @Test
    void callsConfluenceSearchTool() {
        ObjectNode results = objectMapper.createObjectNode();
        results.putArray("results").addObject().put("title", "Runbook");
        when(confluenceTools.search("siteSearch ~ \"runbook\" AND type = page", 5)).thenReturn(results);

        ObjectNode request = request(4, "tools/call");
        ObjectNode params = request.putObject("params");
        params.put("name", "confluence_search");
        params.putObject("arguments")
                .put("cql", "siteSearch ~ \"runbook\" AND type = page")
                .put("limit", 5);

        var response = handler.handle(request);

        assertThat(response.path("result").path("isError").asBoolean()).isFalse();
        assertThat(response.path("result").path("content").get(0).path("text").asText()).contains("\"title\" : \"Runbook\"");
    }

    @Test
    void callsConfluenceListSpacesTool() {
        ObjectNode spaces = objectMapper.createObjectNode();
        spaces.putArray("results").addObject().put("key", "DEV");
        when(confluenceTools.listSpaces(10)).thenReturn(spaces);

        ObjectNode request = request(3, "tools/call");
        ObjectNode params = request.putObject("params");
        params.put("name", "confluence_list_spaces");
        params.putObject("arguments").put("limit", 10);

        var response = handler.handle(request);

        assertThat(response.path("result").path("isError").asBoolean()).isFalse();
        assertThat(response.path("result").path("content").get(0).path("text").asText()).contains("\"key\" : \"DEV\"");
    }

    @Test
    void notificationDoesNotReturnResponse() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("method", "notifications/initialized");

        assertThat(handler.handle(request)).isNull();
    }

    private ObjectNode request(int id, String method) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        return request;
    }
}
