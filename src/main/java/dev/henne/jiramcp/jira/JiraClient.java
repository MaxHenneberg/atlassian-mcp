package dev.henne.jiramcp.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.henne.jiramcp.auth.AtlassianAuth;
import dev.henne.jiramcp.config.JiraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Locale;

@Component
public class JiraClient {

    private static final Logger log = LoggerFactory.getLogger(JiraClient.class);

    private final RestClient restClient;
    private final JiraProperties properties;
    private final ObjectMapper objectMapper;

    public JiraClient(RestClient.Builder restClientBuilder, JiraProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.AUTHORIZATION, AtlassianAuth.authorizationHeader(properties))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public JsonNode getIssue(String issueKey, List<String> fields) {
        URI uri = UriComponentsBuilder
                .fromUriString(properties.baseUrl())
                .pathSegment("rest", "api", properties.apiVersion(), "issue", issueKey)
                .queryParam("fields", String.join(",", fields == null ? List.of() : fields))
                .build()
                .encode()
                .toUri();

        log.debug("Reading Jira issue. issueKey={} uri={} authMode={} fields={}",
                issueKey, uri, properties.authMode(), fields);

        try {
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException exception) {
            String responseBody = exception.getResponseBodyAsString();
            log.error(
                    "Jira issue read failed. issueKey={} uri={} status={} authMode={} responseBody={}",
                    issueKey,
                    uri,
                    exception.getStatusCode().value(),
                    properties.authMode(),
                    responseBody,
                    exception
            );
            throw new JiraClientException(
                    "Jira returned HTTP " + exception.getStatusCode().value() + " for issue " + issueKey
                            + ". See server logs for Jira response body.",
                    exception.getStatusCode().value(),
                    responseBody,
                    uri.toString(),
                    exception
            );
        }
    }

    public JsonNode addComment(String issueKey, String comment) {
        URI uri = jiraUri("issue", issueKey, "comment");
        ObjectNode body = objectMapper.createObjectNode();
        body.set("body", adfDocument(comment));

        log.debug("Adding Jira comment. issueKey={} uri={} authMode={}", issueKey, uri, properties.authMode());

        try {
            return restClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException exception) {
            throw jiraException("Jira comment add failed", issueKey, uri, exception);
        }
    }

    public JsonNode transitionIssue(String issueKey, String targetStatusName) {
        JsonNode transitions = getTransitions(issueKey);
        JsonNode transition = findTransition(transitions, targetStatusName);
        String transitionId = transition.path("id").asText();
        String transitionName = transition.path("name").asText();

        URI uri = jiraUri("issue", issueKey, "transitions");
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("transition").put("id", transitionId);

        log.debug("Transitioning Jira issue. issueKey={} targetStatus={} transitionId={} transitionName={} uri={}",
                issueKey, targetStatusName, transitionId, transitionName, uri);

        try {
            restClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            ObjectNode result = objectMapper.createObjectNode();
            result.put("issueKey", issueKey);
            result.put("requestedStatus", targetStatusName);
            result.put("transitionId", transitionId);
            result.put("transitionName", transitionName);
            result.put("transitioned", true);
            return result;
        } catch (HttpStatusCodeException exception) {
            throw jiraException("Jira transition failed", issueKey, uri, exception);
        }
    }

    public JsonNode getTransitions(String issueKey) {
        URI uri = jiraUri("issue", issueKey, "transitions");
        log.debug("Reading Jira transitions. issueKey={} uri={} authMode={}", issueKey, uri, properties.authMode());

        try {
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException exception) {
            throw jiraException("Jira transitions read failed", issueKey, uri, exception);
        }
    }

    private JsonNode findTransition(JsonNode transitionsResponse, String targetStatusName) {
        String normalizedTarget = targetStatusName.trim().toLowerCase(Locale.ROOT);
        for (JsonNode transition : transitionsResponse.path("transitions")) {
            String name = transition.path("name").asText("").trim().toLowerCase(Locale.ROOT);
            String toName = transition.path("to").path("name").asText("").trim().toLowerCase(Locale.ROOT);
            if (normalizedTarget.equals(name) || normalizedTarget.equals(toName)) {
                return transition;
            }
        }
        throw new IllegalArgumentException("No Jira transition found for target status: " + targetStatusName);
    }

    private URI jiraUri(String... pathSegments) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.baseUrl())
                .pathSegment("rest", "api", properties.apiVersion());
        for (String pathSegment : pathSegments) {
            builder.pathSegment(pathSegment);
        }
        return builder.build().encode().toUri();
    }

    private ObjectNode adfDocument(String text) {
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("type", "doc");
        doc.put("version", 1);
        ObjectNode paragraph = doc.putArray("content").addObject();
        paragraph.put("type", "paragraph");
        ObjectNode textNode = paragraph.putArray("content").addObject();
        textNode.put("type", "text");
        textNode.put("text", text);
        return doc;
    }

    private JiraClientException jiraException(String action, String issueKey, URI uri, HttpStatusCodeException exception) {
        String responseBody = exception.getResponseBodyAsString();
        log.error(
                "{}. issueKey={} uri={} status={} authMode={} responseBody={}",
                action,
                issueKey,
                uri,
                exception.getStatusCode().value(),
                properties.authMode(),
                responseBody,
                exception
        );
        return new JiraClientException(
                action + ". Jira returned HTTP " + exception.getStatusCode().value() + " for issue " + issueKey
                        + ". See server logs for Jira response body.",
                exception.getStatusCode().value(),
                responseBody,
                uri.toString(),
                exception
        );
    }
}
