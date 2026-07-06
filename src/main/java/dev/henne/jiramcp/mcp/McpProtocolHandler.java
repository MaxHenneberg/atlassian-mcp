package dev.henne.jiramcp.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.henne.jiramcp.config.McpToolExposure;
import dev.henne.jiramcp.tools.ConfluenceTools;
import dev.henne.jiramcp.tools.JiraTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static dev.henne.jiramcp.mcp.McpToolCatalog.TOOL_ADD_COMMENT;
import static dev.henne.jiramcp.mcp.McpToolCatalog.TOOL_GET_ISSUE;
import static dev.henne.jiramcp.mcp.McpToolCatalog.TOOL_LIST_CONFLUENCE_SPACES;
import static dev.henne.jiramcp.mcp.McpToolCatalog.TOOL_SEARCH_CONFLUENCE;
import static dev.henne.jiramcp.mcp.McpToolCatalog.TOOL_TRANSITION_ISSUE;

@Component
public class McpProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(McpProtocolHandler.class);
    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final ObjectMapper objectMapper;
    private final JiraTools jiraTools;
    private final ConfluenceTools confluenceTools;
    private final McpToolExposure toolExposure;

    public McpProtocolHandler(
            ObjectMapper objectMapper,
            JiraTools jiraTools,
            ConfluenceTools confluenceTools,
            McpToolExposure toolExposure
    ) {
        this.objectMapper = objectMapper;
        this.jiraTools = jiraTools;
        this.confluenceTools = confluenceTools;
        this.toolExposure = toolExposure;
    }

    public JsonNode handle(JsonNode request) {
        JsonNode id = request.get("id");
        String method = request.path("method").asText();
        if (id == null || id.isNull()) {
            log.debug("Accepted MCP notification method={}", method);
            return null;
        }

        try {
            return switch (method) {
                case "initialize" -> result(id, initializeResult());
                case "ping" -> result(id, objectMapper.createObjectNode());
                case "tools/list" -> result(id, toolsListResult());
                case "tools/call" -> result(id, callTool(request.path("params")));
                case "resources/list" -> result(id, emptyListResult("resources"));
                case "prompts/list" -> result(id, emptyListResult("prompts"));
                default -> error(id, -32601, "Method not found: " + method);
            };
        } catch (IllegalArgumentException exception) {
            log.warn("MCP request failed with invalid params. method={} id={} message={}", method, id, exception.getMessage());
            return error(id, -32602, exception.getMessage());
        } catch (Exception exception) {
            log.error("MCP request failed. method={} id={}", method, id, exception);
            return error(id, -32000, exception.getMessage());
        }
    }

    private ObjectNode initializeResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "jira-mcp-server");
        serverInfo.put("version", "0.1.0");
        result.put("instructions", "Atlassian MCP server with strictly separated Jira and Confluence tools. Only tools exposed by the server config are available. Use tools/list before tools/call.");
        return result;
    }

    private ObjectNode toolsListResult() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        if (toolExposure.isExposed(TOOL_GET_ISSUE)) {
            addJiraGetIssueTool(tools);
        }
        if (toolExposure.isExposed(TOOL_ADD_COMMENT)) {
            addJiraAddCommentTool(tools);
        }
        if (toolExposure.isExposed(TOOL_TRANSITION_ISSUE)) {
            addJiraTransitionIssueTool(tools);
        }
        if (toolExposure.isExposed(TOOL_LIST_CONFLUENCE_SPACES)) {
            addConfluenceListSpacesTool(tools);
        }
        if (toolExposure.isExposed(TOOL_SEARCH_CONFLUENCE)) {
            addConfluenceSearchTool(tools);
        }
        return result;
    }

    private ObjectNode callTool(JsonNode params) throws JsonProcessingException {
        String name = params.path("name").asText();
        if (McpToolCatalog.isKnownTool(name) && !toolExposure.isExposed(name)) {
            throw new IllegalArgumentException("Tool is not exposed by config: " + name);
        }

        JsonNode arguments = params.path("arguments");
        JsonNode toolResult = switch (name) {
            case TOOL_GET_ISSUE -> {
                String issueKey = arguments.path("issueKey").asText(null);
                List<String> fields = readStringArray(arguments.path("fields"));
                log.info("Calling MCP tool {} issueKey={}", TOOL_GET_ISSUE, issueKey);
                yield jiraTools.getIssue(issueKey, fields);
            }
            case TOOL_ADD_COMMENT -> {
                String issueKey = arguments.path("issueKey").asText(null);
                String comment = arguments.path("comment").asText(null);
                log.info("Calling MCP tool {} issueKey={}", TOOL_ADD_COMMENT, issueKey);
                yield jiraTools.addComment(issueKey, comment);
            }
            case TOOL_TRANSITION_ISSUE -> {
                String issueKey = arguments.path("issueKey").asText(null);
                String targetStatus = arguments.path("targetStatus").asText(null);
                log.info("Calling MCP tool {} issueKey={} targetStatus={}", TOOL_TRANSITION_ISSUE, issueKey, targetStatus);
                yield jiraTools.transitionIssue(issueKey, targetStatus);
            }
            case TOOL_LIST_CONFLUENCE_SPACES -> {
                int limit = arguments.path("limit").asInt(25);
                log.info("Calling MCP tool {} limit={}", TOOL_LIST_CONFLUENCE_SPACES, limit);
                yield confluenceTools.listSpaces(limit);
            }
            case TOOL_SEARCH_CONFLUENCE -> {
                String cql = arguments.path("cql").asText(null);
                int limit = arguments.path("limit").asInt(10);
                log.info("Calling MCP tool {} limit={}", TOOL_SEARCH_CONFLUENCE, limit);
                yield confluenceTools.search(cql, limit);
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };

        ObjectNode result = objectMapper.createObjectNode();
        result.put("isError", false);
        ObjectNode content = result.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(toolResult));
        return result;
    }

    private void addJiraGetIssueTool(ArrayNode tools) {
        ObjectNode tool = tools.addObject();
        tool.put("name", TOOL_GET_ISSUE);
        tool.put("description", "Read a Jira issue by issue key, for example PROJ-123. Request the smallest safe field set needed for the task. If fields is omitted or empty, no Jira fields are exported. Allowed fields are: summary, status, created, updated, description, issuetype, priority, labels, components, resolution. User identity, comment, watcher, voter, attachment, worklog, and other PII-prone fields are not available.");
        ObjectNode schema = objectSchema(tool);
        ObjectNode properties = schema.putObject("properties");
        addStringProperty(properties, "issueKey", "Jira issue key or id.");
        addStringArrayProperty(properties, "fields", "Optional allowlisted Jira fields to export. Use as few as possible. Allowed values: summary, status, created, updated, description, issuetype, priority, labels, components, resolution. Omit or pass an empty array to export no fields.");
        schema.putArray("required").add("issueKey");
        schema.put("additionalProperties", false);
    }

    private void addJiraAddCommentTool(ArrayNode tools) {
        ObjectNode tool = tools.addObject();
        tool.put("name", TOOL_ADD_COMMENT);
        tool.put("description", "Add a plain-text comment to a Jira issue.");
        ObjectNode schema = objectSchema(tool);
        ObjectNode properties = schema.putObject("properties");
        addStringProperty(properties, "issueKey", "Jira issue key or id.");
        addStringProperty(properties, "comment", "Comment text to add to the Jira issue.");
        schema.putArray("required").add("issueKey").add("comment");
        schema.put("additionalProperties", false);
    }

    private void addJiraTransitionIssueTool(ArrayNode tools) {
        ObjectNode tool = tools.addObject();
        tool.put("name", TOOL_TRANSITION_ISSUE);
        tool.put("description", "Transition a Jira issue to a target status such as DONE.");
        ObjectNode schema = objectSchema(tool);
        ObjectNode properties = schema.putObject("properties");
        addStringProperty(properties, "issueKey", "Jira issue key or id.");
        addStringProperty(properties, "targetStatus", "Target Jira status or transition name, for example DONE.");
        schema.putArray("required").add("issueKey").add("targetStatus");
        schema.put("additionalProperties", false);
    }

    private void addConfluenceListSpacesTool(ArrayNode tools) {
        ObjectNode tool = tools.addObject();
        tool.put("name", TOOL_LIST_CONFLUENCE_SPACES);
        tool.put("description", "List Confluence spaces from the Atlassian site configured for this server.");
        ObjectNode schema = objectSchema(tool);
        ObjectNode properties = schema.putObject("properties");
        ObjectNode limit = properties.putObject("limit");
        limit.put("type", "integer");
        limit.put("description", "Maximum number of spaces to return. Defaults to 25.");
        limit.put("minimum", 1);
        limit.put("maximum", 250);
        schema.put("additionalProperties", false);
    }

    private void addConfluenceSearchTool(ArrayNode tools) {
        ObjectNode tool = tools.addObject();
        tool.put("name", TOOL_SEARCH_CONFLUENCE);
        tool.put("description", "Search Confluence with CQL to retrieve focused context for RAG-style agent workflows. Use narrow CQL queries and small limits to fetch only context relevant to the current task.");
        ObjectNode schema = objectSchema(tool);
        ObjectNode properties = schema.putObject("properties");
        addStringProperty(properties, "cql", "Confluence CQL search query, for example: siteSearch ~ \"release notes\" AND type = page.");
        ObjectNode limit = properties.putObject("limit");
        limit.put("type", "integer");
        limit.put("description", "Maximum number of search results to return. Defaults to 10.");
        limit.put("minimum", 1);
        limit.put("maximum", 50);
        schema.putArray("required").add("cql");
        schema.put("additionalProperties", false);
    }

    private ObjectNode objectSchema(ObjectNode tool) {
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        return schema;
    }

    private void addStringProperty(ObjectNode properties, String name, String description) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "string");
        property.put("description", description);
    }

    private void addStringArrayProperty(ObjectNode properties, String name, String description) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "array");
        property.put("description", description);
        property.putObject("items").put("type", "string");
        property.put("uniqueItems", true);
    }

    private List<String> readStringArray(JsonNode arrayNode) {
        if (arrayNode == null || arrayNode.isMissingNode() || arrayNode.isNull()) {
            return List.of();
        }
        if (!arrayNode.isArray()) {
            throw new IllegalArgumentException("fields must be an array of strings");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : arrayNode) {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("fields must be an array of strings");
            }
            values.add(value.asText());
        }
        return values;
    }

    private ObjectNode emptyListResult(String fieldName) {
        ObjectNode result = objectMapper.createObjectNode();
        result.putArray(fieldName);
        return result;
    }

    private ObjectNode result(JsonNode id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message == null ? "Unknown error" : message);
        return response;
    }
}
