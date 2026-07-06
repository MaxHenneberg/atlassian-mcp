# Jira MCP Server

Spring Boot based MCP server that exposes Jira and Confluence reads to AI agents through a local HTTP MCP endpoint.

Implemented tool:

- `jira_get_issue`: reads one Jira issue by key or id with explicit allowlisted fields only.
- `jira_add_comment`: adds a plain-text comment to a Jira issue.
- `jira_transition_issue`: transitions a Jira issue by target status or transition name.
- `confluence_list_spaces`: lists Confluence spaces from the same Atlassian site.
- `confluence_search`: searches Confluence with CQL for focused RAG-style context retrieval.

## Configuration

Atlassian credentials and server settings are supplied through environment
variables. MCP tool exposure is supplied through a JSON file selected by
`MCP_TOOLS_CONFIG`.

| Variable | Required | Default | Description |
| --- | --- | --- | --- |
| `JIRA_BASE_URL` | yes | | Base URL, for example `https://jira.example.com` |
| `JIRA_PAT` | yes | | Jira Personal Access Token, or Jira Cloud API token when `JIRA_AUTH_MODE=basic` |
| `JIRA_AUTH_MODE` | no | `bearer` | `bearer` for Jira Server/Data Center PATs, `basic` for Jira Cloud |
| `JIRA_EMAIL` | only for `basic` | | Atlassian account email for Jira Cloud basic auth |
| `JIRA_API_VERSION` | no | `3` | Jira REST API version in `/rest/api/{version}` |
| `MCP_TOOLS_CONFIG` | no | `mcp-tools.json` | Path to the JSON file that explicitly exposes MCP tools |

With the default `bearer` mode, authentication uses:

```http
Authorization: Bearer <JIRA_PAT>
```

That matches Jira Server/Data Center PAT usage. For Jira Cloud (`*.atlassian.net`), use `JIRA_AUTH_MODE=basic`, `JIRA_EMAIL`, and an Atlassian API token as `JIRA_PAT`.

## MCP Tool Exposure Config

The server reads one JSON config file during startup. Only tools listed in this
file are exposed through `tools/list` and accepted by `tools/call`.

Jira and Confluence tools are configured in separate sections, but in the same
file. A tool in the wrong section or an unknown tool name fails startup.

Complete config with all currently implemented tools enabled:

```json
{
  "jira": {
    "tools": [
      "jira_get_issue",
      "jira_add_comment",
      "jira_transition_issue"
    ]
  },
  "confluence": {
    "tools": [
      "confluence_list_spaces",
      "confluence_search"
    ]
  }
}
```

To expose only read-only Jira and Confluence search, for example:

```json
{
  "jira": {
    "tools": ["jira_get_issue"]
  },
  "confluence": {
    "tools": ["confluence_search"]
  }
}
```

If a section is omitted, no tools from that product are exposed. If a section is
present, it must contain an explicit `tools` array.

## Build

```bash
mvn test
mvn package
docker build -t jira-mcp-server:latest .
```

## Run Locally

```bash
JIRA_BASE_URL=https://jira.example.com \
JIRA_PAT=your-token \
java -jar target/jira-mcp-server-0.1.0-SNAPSHOT.jar
```

The server listens on `http://localhost:8080/mcp` by default. Override the port
with `PORT`.

## Docker

```bash
docker run --rm -i \
  -p 8080:8080 \
  -v "$PWD/mcp-tools.json:/config/mcp-tools.json:ro" \
  -e JIRA_BASE_URL=https://jira.example.com \
  -e JIRA_PAT=your-token \
  -e MCP_TOOLS_CONFIG=/config/mcp-tools.json \
  jira-mcp-server:latest
```

## Docker Compose

Set the environment variables in your shell or in a local `.env` file:

```env
JIRA_BASE_URL=https://henneberg.atlassian.net
JIRA_AUTH_MODE=basic
JIRA_EMAIL=your.atlassian.email@example.com
JIRA_PAT=your-atlassian-api-token
PORT=8080
```

Place your tool exposure JSON as `mcp-tools.json` in the same directory as
`compose.yaml`. With the repository layout this means:

```text
atlassian-mcp/
  compose.yaml
  mcp-tools.json
```

The provided Compose file uses `./mcp-tools.json`, so the path is resolved
relative to the directory containing `compose.yaml` when you run
`docker compose up`. It mounts the file read-only into the container:

```yaml
volumes:
  - ./mcp-tools.json:/config/mcp-tools.json:ro
environment:
  MCP_TOOLS_CONFIG: /config/mcp-tools.json
```

Then start the server:

```bash
docker compose up --build
```

Verify what Compose will pass into the container:

```bash
docker compose config
```

The MCP endpoint is available at:

```text
http://localhost:8080/mcp
```

For Jira Cloud:

```bash
docker run --rm -i \
  -p 8080:8080 \
  -v "$PWD/mcp-tools.json:/config/mcp-tools.json:ro" \
  -e JIRA_BASE_URL=https://henneberg.atlassian.net \
  -e JIRA_AUTH_MODE=basic \
  -e JIRA_EMAIL=your.atlassian.email@example.com \
  -e JIRA_PAT=your-atlassian-api-token \
  -e MCP_TOOLS_CONFIG=/config/mcp-tools.json \
  jira-mcp-server:latest
```

The Docker image also contains the repository's `mcp-tools.json` at
`/app/mcp-tools.json`, which enables all tools. Mount your own file and set
`MCP_TOOLS_CONFIG` when you want a different exposure policy.

## Smoke Test

```bash
curl -s http://localhost:8080/health
```

List MCP tools:

```bash
curl -s http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

Read an issue:

```bash
curl -s http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"jira_get_issue","arguments":{"issueKey":"SCRUM-1","fields":["summary","status"]}}}'
```

## Codex Configuration

Codex supports streamable HTTP MCP servers configured in `~/.codex/config.toml`
or project-scoped `.codex/config.toml`.

```toml
[mcp_servers.jira]
url = "http://localhost:8080/mcp"
startup_timeout_sec = 20
tool_timeout_sec = 60
```

## Other MCP Clients

For clients such as opencode-ai or IntelliJ AI Chat, configure a streamable HTTP
MCP server with this URL:

```text
http://localhost:8080/mcp
```

The Jira issue read tool input schema is:

```json
{
  "issueKey": "PROJ-123",
  "fields": ["summary", "status"]
}
```

`fields` is optional. If it is omitted or empty, no Jira fields are exported. Agents should request as few fields as possible. Allowed values are `summary`, `status`, `created`, `updated`, `description`, `issuetype`, `priority`, `labels`, `components`, and `resolution`. User identity, comment, watcher, voter, attachment, worklog, and other PII-prone fields are not available.

The Jira comment tool input schema is:

```json
{
  "issueKey": "PROJ-123",
  "comment": "Implemented the requested changes."
}
```

The Jira transition tool input schema is:

```json
{
  "issueKey": "PROJ-123",
  "targetStatus": "DONE"
}
```

The Confluence spaces tool input schema is:

```json
{
  "limit": 25
}
```

The Confluence search tool input schema is:

```json
{
  "cql": "siteSearch ~ \"runbook\" AND type = page",
  "limit": 10
}
```

Use focused CQL and small limits so agents retrieve only the context needed for the current task.

## Troubleshooting

### Jira returns 404

The server logs verbose Jira request failures, including issue key, Jira REST
URL, HTTP status, auth mode, and Jira's response body. Tokens are not logged.

For Jira Cloud, make sure you use the site base URL and basic auth mode:

```bash
docker run --rm -i \
  -p 8080:8080 \
  -e JIRA_BASE_URL=https://henneberg.atlassian.net \
  -e JIRA_AUTH_MODE=basic \
  -e JIRA_EMAIL=your.atlassian.email@example.com \
  -e JIRA_PAT=your-atlassian-api-token \
  jira-mcp-server:latest
```

Then call `jira_get_issue` for `SCRUM-1` and inspect the Docker logs.
