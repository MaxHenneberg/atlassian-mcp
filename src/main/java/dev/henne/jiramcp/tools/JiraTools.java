package dev.henne.jiramcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.henne.jiramcp.jira.JiraClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JiraTools {

    private static final Set<String> ALLOWED_READ_FIELDS = Set.of(
            "summary",
            "status",
            "created",
            "updated",
            "description",
            "issuetype",
            "priority",
            "labels",
            "components",
            "resolution"
    );

    private final JiraClient jiraClient;

    public JiraTools(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    public JsonNode getIssue(String issueKey, List<String> fields) {
        if (issueKey == null || issueKey.isBlank()) {
            throw new IllegalArgumentException("issueKey is required");
        }
        return jiraClient.getIssue(issueKey.trim(), normalizeFields(fields));
    }

    public JsonNode addComment(String issueKey, String comment) {
        if (issueKey == null || issueKey.isBlank()) {
            throw new IllegalArgumentException("issueKey is required");
        }
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("comment is required");
        }
        return jiraClient.addComment(issueKey.trim(), comment.trim());
    }

    public JsonNode transitionIssue(String issueKey, String targetStatus) {
        if (issueKey == null || issueKey.isBlank()) {
            throw new IllegalArgumentException("issueKey is required");
        }
        if (targetStatus == null || targetStatus.isBlank()) {
            throw new IllegalArgumentException("targetStatus is required");
        }
        return jiraClient.transitionIssue(issueKey.trim(), targetStatus.trim());
    }

    private List<String> normalizeFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        return fields.stream()
                .map(field -> field == null ? "" : field.trim().toLowerCase(Locale.ROOT))
                .filter(field -> !field.isBlank())
                .peek(field -> {
                    if (!ALLOWED_READ_FIELDS.contains(field)) {
                        throw new IllegalArgumentException("Field is not allowed for jira_get_issue: " + field
                                + ". Allowed fields: " + String.join(", ", ALLOWED_READ_FIELDS));
                    }
                })
                .distinct()
                .collect(Collectors.toList());
    }
}
