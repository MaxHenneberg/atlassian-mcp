package dev.henne.jiramcp.tools;

import dev.henne.jiramcp.jira.JiraClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JiraToolsTest {

    private final JiraClient jiraClient = mock(JiraClient.class);
    private final JiraTools jiraTools = new JiraTools(jiraClient);

    @Test
    void getIssueNormalizesAllowedFields() {
        jiraTools.getIssue(" SCRUM-3 ", List.of(" Summary ", "STATUS", "summary"));

        verify(jiraClient).getIssue("SCRUM-3", List.of("summary", "status"));
    }

    @Test
    void getIssueRejectsPiiProneFields() {
        assertThatThrownBy(() -> jiraTools.getIssue("SCRUM-3", List.of("reporter")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field is not allowed");
    }
}
