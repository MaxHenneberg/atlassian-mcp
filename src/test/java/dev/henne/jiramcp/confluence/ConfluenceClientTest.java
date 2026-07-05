package dev.henne.jiramcp.confluence;

import com.fasterxml.jackson.databind.JsonNode;
import dev.henne.jiramcp.config.JiraProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class ConfluenceClientTest {

    @Test
    void listSpacesUsesConfluencePathAndSharedAuth() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"results":[{"key":"DEV","name":"Development"}]}
                            """));
            server.start();

            JiraProperties properties = new JiraProperties(
                    server.url("/").toString(),
                    "api-token",
                    "user@example.com",
                    "basic",
                    "3"
            );
            ConfluenceClient client = new ConfluenceClient(RestClient.builder(), properties);

            JsonNode spaces = client.listSpaces(10);

            assertThat(spaces.path("results").get(0).path("key").asText()).isEqualTo("DEV");
            RecordedRequest request = server.takeRequest();
            assertThat(request.getHeader("Authorization")).isEqualTo("Basic dXNlckBleGFtcGxlLmNvbTphcGktdG9rZW4=");
            assertThat(request.getPath()).isEqualTo("/wiki/rest/api/space?limit=10");
        }
    }

    @Test
    void searchUsesConfluenceSearchPathAndCql() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"results":[{"title":"Runbook","excerpt":"Deploy steps"}]}
                            """));
            server.start();

            JiraProperties properties = new JiraProperties(
                    server.url("/").toString(),
                    "api-token",
                    "user@example.com",
                    "basic",
                    "3"
            );
            ConfluenceClient client = new ConfluenceClient(RestClient.builder(), properties);

            JsonNode results = client.search("siteSearch ~ \"runbook\" AND type = page", 5);

            assertThat(results.path("results").get(0).path("title").asText()).isEqualTo("Runbook");
            RecordedRequest request = server.takeRequest();
            assertThat(request.getHeader("Authorization")).isEqualTo("Basic dXNlckBleGFtcGxlLmNvbTphcGktdG9rZW4=");
            assertThat(request.getPath()).isEqualTo("/wiki/rest/api/search?cql=siteSearch%20~%20%22runbook%22%20AND%20type%20%3D%20page&limit=5");
        }
    }
}
