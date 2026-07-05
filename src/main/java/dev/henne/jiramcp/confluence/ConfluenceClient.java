package dev.henne.jiramcp.confluence;

import com.fasterxml.jackson.databind.JsonNode;
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

@Component
public class ConfluenceClient {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceClient.class);

    private final RestClient restClient;
    private final JiraProperties properties;

    public ConfluenceClient(RestClient.Builder restClientBuilder, JiraProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.AUTHORIZATION, AtlassianAuth.authorizationHeader(properties))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public JsonNode listSpaces(int limit) {
        URI uri = UriComponentsBuilder
                .fromUriString(properties.baseUrl())
                .pathSegment("wiki", "rest", "api", "space")
                .queryParam("limit", limit)
                .build()
                .encode()
                .toUri();

        log.debug("Listing Confluence spaces. uri={} authMode={}", uri, properties.authMode());

        try {
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException exception) {
            String responseBody = exception.getResponseBodyAsString();
            log.error(
                    "Confluence spaces list failed. uri={} status={} authMode={} responseBody={}",
                    uri,
                    exception.getStatusCode().value(),
                    properties.authMode(),
                    responseBody,
                    exception
            );
            throw new ConfluenceClientException(
                    "Confluence returned HTTP " + exception.getStatusCode().value() + " while listing spaces."
                            + " See server logs for response body.",
                    exception.getStatusCode().value(),
                    responseBody,
                    uri.toString(),
                    exception
            );
        }
    }

    public JsonNode search(String cql, int limit) {
        URI uri = UriComponentsBuilder
                .fromUriString(properties.baseUrl())
                .pathSegment("wiki", "rest", "api", "search")
                .queryParam("cql", cql)
                .queryParam("limit", limit)
                .build()
                .encode()
                .toUri();

        log.debug("Searching Confluence. uri={} authMode={}", uri, properties.authMode());

        try {
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException exception) {
            String responseBody = exception.getResponseBodyAsString();
            log.error(
                    "Confluence search failed. uri={} status={} authMode={} responseBody={}",
                    uri,
                    exception.getStatusCode().value(),
                    properties.authMode(),
                    responseBody,
                    exception
            );
            throw new ConfluenceClientException(
                    "Confluence returned HTTP " + exception.getStatusCode().value() + " while searching."
                            + " See server logs for response body.",
                    exception.getStatusCode().value(),
                    responseBody,
                    uri.toString(),
                    exception
            );
        }
    }
}
