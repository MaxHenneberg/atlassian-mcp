package dev.henne.jiramcp.jira;

public class JiraClientException extends RuntimeException {

    private final int statusCode;
    private final String jiraResponseBody;
    private final String requestUri;

    public JiraClientException(String message, int statusCode, String jiraResponseBody, String requestUri, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.jiraResponseBody = jiraResponseBody;
        this.requestUri = requestUri;
    }

    public int statusCode() {
        return statusCode;
    }

    public String jiraResponseBody() {
        return jiraResponseBody;
    }

    public String requestUri() {
        return requestUri;
    }
}
