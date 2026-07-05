package dev.henne.jiramcp.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpHttpControllerTest {

    private final McpProtocolHandler protocolHandler = mock(McpProtocolHandler.class);
    private final McpHttpController controller = new McpHttpController(protocolHandler);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void postMcpReturnsJsonRpcResponse() {
        var request = objectMapper.createObjectNode().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/list");
        var responseBody = objectMapper.createObjectNode().put("jsonrpc", "2.0").put("id", 1);
        when(protocolHandler.handle(request)).thenReturn(responseBody);

        var response = controller.postMcp(request, "2025-06-18", null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isSameAs(responseBody);
    }

    @Test
    void postMcpReturnsAcceptedForNotifications() {
        var request = objectMapper.createObjectNode().put("jsonrpc", "2.0").put("method", "notifications/initialized");
        when(protocolHandler.handle(request)).thenReturn(null);

        var response = controller.postMcp(request, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNull();
    }
}
