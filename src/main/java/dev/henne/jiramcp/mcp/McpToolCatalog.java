package dev.henne.jiramcp.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpToolCatalog {

    public static final String TOOL_GET_ISSUE = "jira_get_issue";
    public static final String TOOL_ADD_COMMENT = "jira_add_comment";
    public static final String TOOL_TRANSITION_ISSUE = "jira_transition_issue";
    public static final String TOOL_LIST_CONFLUENCE_SPACES = "confluence_list_spaces";
    public static final String TOOL_SEARCH_CONFLUENCE = "confluence_search";

    public static final List<String> JIRA_TOOLS = List.of(
            TOOL_GET_ISSUE,
            TOOL_ADD_COMMENT,
            TOOL_TRANSITION_ISSUE
    );

    public static final List<String> CONFLUENCE_TOOLS = List.of(
            TOOL_LIST_CONFLUENCE_SPACES,
            TOOL_SEARCH_CONFLUENCE
    );

    public static final Map<String, List<String>> TOOLS_BY_DOMAIN = toolsByDomain();

    private McpToolCatalog() {
    }

    public static boolean isKnownTool(String toolName) {
        return JIRA_TOOLS.contains(toolName) || CONFLUENCE_TOOLS.contains(toolName);
    }

    private static Map<String, List<String>> toolsByDomain() {
        Map<String, List<String>> toolsByDomain = new LinkedHashMap<>();
        toolsByDomain.put("jira", JIRA_TOOLS);
        toolsByDomain.put("confluence", CONFLUENCE_TOOLS);
        return Collections.unmodifiableMap(toolsByDomain);
    }
}
