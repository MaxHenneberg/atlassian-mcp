package dev.henne.jiramcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class McpHttpController {

    private static final Logger log = LoggerFactory.getLogger(McpHttpController.class);

    private final McpProtocolHandler protocolHandler;

    public McpHttpController(McpProtocolHandler protocolHandler) {
        this.protocolHandler = protocolHandler;
    }

    @PostMapping(path = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> postMcp(
            @RequestBody JsonNode request,
            @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
            @RequestHeader(value = "Origin", required = false) String origin
    ) {
        log.debug("Received MCP HTTP request method={} id={} protocolVersion={} origin={}",
                request.path("method").asText(null), request.path("id").asText(null), protocolVersion, origin);

        JsonNode response = protocolHandler.handle(request);
        if (response == null) {
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping(path = "/mcp")
    public ResponseEntity<Void> getMcp() {
        log.debug("Received MCP GET request; SSE stream is not implemented");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @GetMapping(path = "/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }
}
