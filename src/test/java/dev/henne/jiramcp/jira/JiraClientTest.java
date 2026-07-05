package dev.henne.jiramcp.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.henne.jiramcp.config.JiraProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JiraClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getIssueUsesBearerTokenAndExplicitFields() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"key":"PROJ-123","fields":{"summary":"Test issue"}}
                            """));
            server.start();

            JiraProperties properties = new JiraProperties(
                    server.url("/jira/").toString(),
                    "test-token",
                    null,
                    "bearer",
                    "3"
            );
            JiraClient client = new JiraClient(RestClient.builder(), properties, objectMapper);

            JsonNode issue = client.getIssue("PROJ-123", List.of("summary", "status"));

            assertThat(issue.path("key").asText()).isEqualTo("PROJ-123");
            RecordedRequest request = server.takeRequest();
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token");
            assertThat(request.getPath()).isEqualTo("/jira/rest/api/3/issue/PROJ-123?fields=summary,status");
        }
    }

    @Test
    void getIssueExportsNoFieldsWhenFieldsAreEmpty() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"key":"PROJ-123","fields":{}}
                            """));
            server.start();

            JiraProperties properties = new JiraProperties(
                    server.url("/jira/").toString(),
                    "test-token",
                    null,
                    "bearer",
                    "3"
            );
            JiraClient client = new JiraClient(RestClient.builder(), properties, objectMapper);

            client.getIssue("PROJ-123", List.of());

            RecordedRequest request = server.takeRequest();
            assertThat(request.getPath()).isEqualTo("/jira/rest/api/3/issue/PROJ-123?fields=");
        }
    }

    @Test
    void getIssueSupportsJiraCloudBasicAuth() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"key":"SCRUM-1"}
                            """));
            server.start();

            JiraProperties properties = new JiraProperties(
                    server.url("/").toString(),
                    "api-token",
                    "user@example.com",
                    "basic",
                    "3"
            );
            JiraClient client = new JiraClient(RestClient.builder(), properties, objectMapper);

            client.getIssue("SCRUM-1", List.of("summary"));

            RecordedRequest request = server.takeRequest();
            assertThat(request.getHeader("Authorization")).isEqualTo("Basic dXNlckBleGFtcGxlLmNvbTphcGktdG9rZW4=");
            assertThat(request.getPath()).isEqualTo("/rest/api/3/issue/SCRUM-1?fields=summary");
        }
    }

    @Test
    void addCommentUsesJiraCloudCommentEndpoint() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"10010","body":{"type":"doc"}}
                            """));
            server.start();

            JiraProperties properties = new JiraProperties(
                    server.url("/").toString(),
                    "api-token",
                    "user@example.com",
                    "basic",
                    "3"
            );
            JiraClient client = new JiraClient(RestClient.builder(), properties, objectMapper);

            JsonNode comment = client.addComment("SCRUM-2", "Implemented changes");

            assertThat(comment.path("id").asText()).isEqualTo("10010");
            RecordedRequest request = server.takeRequest();
            assertThat(request.getMethod()).isEqualTo("POST");
            assertThat(request.getPath()).isEqualTo("/rest/api/3/issue/SCRUM-2/comment");
            assertThat(request.getBody().readUtf8()).contains("\"text\":\"Implemented changes\"");
        }
    }

    @Test
    void transitionIssueFindsTargetStatusAndPostsTransition() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"transitions":[{"id":"31","name":"Done","to":{"name":"Done"}}]}
                            """));
            server.enqueue(new MockResponse().setResponseCode(204));
            server.start();

            JiraProperties properties = new JiraProperties(
                    server.url("/").toString(),
                    "api-token",
                    "user@example.com",
                    "basic",
                    "3"
            );
            JiraClient client = new JiraClient(RestClient.builder(), properties, objectMapper);

            JsonNode transition = client.transitionIssue("SCRUM-2", "DONE");

            assertThat(transition.path("transitioned").asBoolean()).isTrue();
            assertThat(transition.path("transitionId").asText()).isEqualTo("31");
            RecordedRequest listRequest = server.takeRequest();
            RecordedRequest postRequest = server.takeRequest();
            assertThat(listRequest.getMethod()).isEqualTo("GET");
            assertThat(listRequest.getPath()).isEqualTo("/rest/api/3/issue/SCRUM-2/transitions");
            assertThat(postRequest.getMethod()).isEqualTo("POST");
            assertThat(postRequest.getPath()).isEqualTo("/rest/api/3/issue/SCRUM-2/transitions");
            assertThat(postRequest.getBody().readUtf8()).contains("\"id\":\"31\"");
        }
    }
}
