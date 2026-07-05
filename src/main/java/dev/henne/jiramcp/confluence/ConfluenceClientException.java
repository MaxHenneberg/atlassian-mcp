package dev.henne.jiramcp.confluence;

public class ConfluenceClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;
    private final String requestUri;

    public ConfluenceClientException(String message, int statusCode, String responseBody, String requestUri, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.requestUri = requestUri;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }

    public String requestUri() {
        return requestUri;
    }
}
