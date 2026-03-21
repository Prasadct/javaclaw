<p align="center">
  <img src="javaclaw-banner.png" alt="Javaclaw Banner" />
</p>

# Javaclaw

**Spring-native agent runtime for secure enterprise workflows in Java.**

Built on [Spring AI](https://docs.spring.io/spring-ai/reference/) | Java 21 | Spring Boot 3.4 | Apache 2.0

---

## What is Javaclaw?

Javaclaw is a lightweight agent framework that runs LLM-powered workflows with built-in security guardrails. It gives you a structured execution loop — think, act, observe — with policy enforcement, approval gates, and a full audit trail on every step. It's designed for teams that need AI agents in production but can't afford to skip governance.

## What makes it different

- **Policy engine** — every tool call is checked against configurable rules before execution. ALLOW, DENY, or REQUIRE_APPROVAL per tool, per agent.
- **Approval gates** — high-risk actions (shell commands, database writes) pause for human approval. No silent side effects.
- **Audit trail** — every thought, tool call, policy decision, and result is recorded. Full traceability from goal to outcome.
- **Spring-native** — auto-configuration, dependency injection, `application.yml`. Feels like building any other Spring Boot app.

## Quick start

```bash
# Clone
git clone https://github.com/your-org/javaclaw.git
cd javaclaw

# Build
mvn clean install

# Set your OpenAI API key
export OPENAI_API_KEY=sk-your-key-here

# Run the demo
cd javaclaw-agent
mvn spring-boot:run
```

## Try it

```bash
curl -s http://localhost:8080/api/agent/task \
  -H "Content-Type: application/json" \
  -d '{"goal": "Find the bug in UserService that causes null pointer exceptions"}' | jq .
```

## REST API

All endpoints are under `/api/agent`.

### Start a task

```bash
curl -s http://localhost:8080/api/agent/task \
  -H "Content-Type: application/json" \
  -d '{"goal": "Find the bug in UserService"}' | jq .
```

Response — the task starts running asynchronously:

```json
{
  "taskId": "a3f1c2d4-...",
  "status": "CREATED",
  "goal": "Find the bug in UserService"
}
```

### Poll task status

```bash
curl -s http://localhost:8080/api/agent/task/{taskId} | jq .
```

When the task is running or completed, you get the full task with audit trail:

```json
{
  "id": "a3f1c2d4-...",
  "goal": "Find the bug in UserService",
  "status": "COMPLETED",
  "result": "The bug is in UserService.getUserCity() — ...",
  "auditTrail": [
    { "type": "TASK_CREATED",   "detail": "Goal: Find the bug in UserService..." },
    { "type": "TASK_STARTED",   "detail": "Agent: bugfix-assistant" },
    { "type": "POLICY_CHECK",   "detail": "tool=search_code decision=ALLOW" },
    { "type": "TOOL_EXECUTED",  "detail": "tool=search_code result=Found 3 match(es)..." },
    { "type": "TASK_COMPLETED", "detail": "The bug is in UserService.getUserCity()..." }
  ]
}
```

When the task is waiting for approval, the response includes `pendingApproval` details inline — no need to call a separate endpoint:

```json
{
  "id": "a3f1c2d4-...",
  "goal": "Delete old log files",
  "status": "WAITING_FOR_APPROVAL",
  "result": null,
  "auditTrail": [ "..." ],
  "pendingApproval": {
    "toolName": "run_command",
    "toolInput": "rm -rf /var/log/old/*.log",
    "riskLevel": "HIGH",
    "reason": "Policy requires approval for tool 'run_command'"
  }
}
```

### Approve or reject a pending action

```bash
# Approve
curl -s http://localhost:8080/api/agent/task/{taskId}/approve \
  -H "Content-Type: application/json" \
  -d '{"approved": true, "reason": "Looks safe"}' | jq .

# Reject
curl -s http://localhost:8080/api/agent/task/{taskId}/approve \
  -H "Content-Type: application/json" \
  -d '{"approved": false, "reason": "Too risky"}' | jq .
```

Response:

```json
{
  "taskId": "a3f1c2d4-...",
  "submitted": true,
  "approved": true
}
```

After approval, the task resumes automatically. After rejection, the task moves to `CANCELLED`.

### List all tasks

```bash
curl -s http://localhost:8080/api/agent/tasks | jq .
```

### List pending approvals

```bash
curl -s http://localhost:8080/api/agent/approvals/pending | jq .
```

### Typical approval workflow

```bash
# 1. Start a task that will trigger a high-risk tool
TASK_ID=$(curl -s http://localhost:8080/api/agent/task \
  -H "Content-Type: application/json" \
  -d '{"goal": "Clean up old temp files"}' | jq -r .taskId)

# 2. Poll until the task needs approval
curl -s http://localhost:8080/api/agent/task/$TASK_ID | jq .
# → status: "WAITING_FOR_APPROVAL", pendingApproval: { toolName: "run_command", ... }

# 3. Review and approve
curl -s http://localhost:8080/api/agent/task/$TASK_ID/approve \
  -H "Content-Type: application/json" \
  -d '{"approved": true, "reason": "Verified the command is safe"}' | jq .

# 4. Poll again — task completes
curl -s http://localhost:8080/api/agent/task/$TASK_ID | jq .
# → status: "COMPLETED"
```

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                     Entry Points                          │
│   REST API (AgentController)  │  Slack Bot (Socket Mode)  │
├──────────────────────────────────────────────────────────┤
│                    Agent Runtime                           │
│           execute loop: think → act → observe              │
├───────────────────────┬──────────────────────────────────┤
│     Tool Registry     │          Policy Engine             │
│  ┌──────────────────┐ │  ┌────────────────────────────┐  │
│  │  read_file        │ │  │  ALLOW / DENY / APPROVAL   │  │
│  │  search_code      │ │  │  per-tool, per-agent rules  │  │
│  │  run_command      │ │  │  risk-level awareness       │  │
│  │  github_read_issue│ │  └────────────────────────────┘  │
│  │  github_read_file │ │                                   │
│  │  github_create_pr │ │                                   │
│  └──────────────────┘ │                                   │
├───────────────────────┴──────────────────────────────────┤
│                 Spring AI (ChatClient)                     │
│              OpenAI / Anthropic / Ollama                   │
└──────────────────────────────────────────────────────────┘
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for details.

## Project structure

```
javaclaw/
├── javaclaw-core/          # Framework library (auto-configuration, runtime, tools)
│   └── com.javaclaw.core
│       ├── model/          # AgentTask, AgentDefinition, ToolDefinition
│       ├── runtime/        # AgentRuntime, ToolRegistry
│       ├── policy/         # PolicyEngine, PolicyDecision
│       ├── tools/          # ReadFileTool, SearchCodeTool, ShellCommandTool
│       ├── tools/github/   # GitHubApiClient, GitHubReadIssueTool, GitHubReadFileTool, GitHubCreatePRTool
│       ├── channel/        # MessageChannel, TaskProgressListener
│       ├── channel/slack/  # SlackAppManager, SlackMessageChannel, SlackTaskProgressListener
│       └── spring/         # JavaclawAutoConfiguration, JavaclawProperties
├── javaclaw-agent/         # Spring Boot agent app
│   ├── sample-repo/        # Sample Java code with intentional bugs
│   └── com.javaclaw.agent  # AgentConfig, AgentController
└── docs/
    └── ARCHITECTURE.md
```

## Configuration

```yaml
javaclaw:
  max-steps: 10                    # Max agent loop iterations
  tools:
    base-directory: ./sample-repo  # Sandbox for file tools
  github:
    token: ${GITHUB_TOKEN:}        # GitHub personal access token (repo scope)
  approval:
    timeout-seconds: 300           # How long to wait for human approval
  slack:
    enabled: false                 # Set to true to enable the Slack bot
    bot-token: ${SLACK_BOT_TOKEN:} # Bot User OAuth Token (xoxb-...)
    app-token: ${SLACK_APP_TOKEN:} # App-Level Token (xapp-...)

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
```

## GitHub Integration

Javaclaw includes three GitHub tools that let the agent read issues, read repository files, and create pull requests — all through the GitHub API.

### Setup

Set the `GITHUB_TOKEN` environment variable to a [personal access token](https://github.com/settings/tokens) with the `repo` scope:

```bash
export GITHUB_TOKEN=ghp_your_token_here
```

The token is configured in `application.yml` under `javaclaw.github.token`.

### Tools

| Tool | Description | Risk Level | Required Input |
|------|-------------|------------|----------------|
| `github_read_issue` | Reads a GitHub issue by number (title, state, labels, body) | LOW | `owner`, `repo`, `issue_number` |
| `github_read_file` | Reads a file from a GitHub repository (max 50 KB) | LOW | `owner`, `repo`, `path`, optional `branch` (default: `main`) |
| `github_create_pr` | Creates a branch, commits a file change, and opens a pull request | HIGH | `owner`, `repo`, `base_branch`, `branch_name`, `file_path`, `new_file_content`, `pr_title`, `pr_body`, `commit_message` |

Since `github_create_pr` is rated **HIGH** risk, the policy engine will pause for human approval before executing it.

### Example workflow

A typical agent flow using GitHub tools:

1. **Read the issue** — agent calls `github_read_issue` to understand the bug report
2. **Read the file** — agent calls `github_read_file` to inspect the relevant source code
3. **Create a PR** — agent calls `github_create_pr` with the fix → policy engine pauses for approval → human approves via REST API or Slack → PR is created

## Slack Integration

Javaclaw supports a conversational Slack interface. Mention the bot in a channel or send it a direct message to start a task. The agent replies in a thread with progress updates and sends interactive **Approve / Reject** buttons when a high-risk action needs human approval.

### Slack App setup

1. Go to [api.slack.com/apps](https://api.slack.com/apps) → **Create New App** → **From Scratch**
2. **Socket Mode** → Enable → Generate an **App-Level Token** (`xapp-...`) with the `connections:write` scope
3. **OAuth & Permissions** → Add the following **Bot Token Scopes**:
   - `chat:write`
   - `app_mentions:read`
   - `im:read`
   - `im:write`
   - `im:history`
4. **Event Subscriptions** → Enable → Subscribe to bot events:
   - `app_mention`
   - `message.im`
5. **Interactivity & Shortcuts** → Enable (no Request URL needed for Socket Mode)
6. **Install to Workspace** → Copy the **Bot User OAuth Token** (`xoxb-...`)

### Configuration

Set the environment variables and enable the integration:

```bash
export SLACK_BOT_TOKEN=xoxb-your-bot-token
export SLACK_APP_TOKEN=xapp-your-app-token
```

Then set `javaclaw.slack.enabled: true` in `application.yml` (or via environment variable).

### Usage

- **Mention in a channel** — `@Javaclaw Find the bug in UserService` → the bot starts a task and replies in a thread
- **Direct message** — send any message to the bot to start a task
- **Progress updates** — the agent posts step-by-step updates (thinking, tool calls, results) as threaded replies
- **Approval buttons** — when a high-risk tool is invoked (e.g. `github_create_pr`), the bot posts an **Approve** / **Reject** button block in the thread. Click to approve or reject; the message updates to confirm your decision and the agent continues or stops accordingly.

## License

[Apache License 2.0](LICENSE)
