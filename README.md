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
cd javaclaw-demo
mvn spring-boot:run
```

## Try it

```bash
curl -s http://localhost:8080/api/agent/task \
  -H "Content-Type: application/json" \
  -d '{"goal": "Find the bug in UserService that causes null pointer exceptions"}' | jq .
```

## Example output

```json
{
  "id": "a3f1c2d4-...",
  "goal": "Find the bug in UserService that causes null pointer exceptions",
  "status": "COMPLETED",
  "result": "The bug is in UserService.getUserCity() — it calls getUser(userId) which returns null when the user doesn't exist, then immediately calls .getAddress().getCity() on the null reference. Fix: add a null check on the user before accessing the address.",
  "auditTrail": [
    { "type": "TASK_CREATED",   "detail": "Goal: Find the bug in UserService..." },
    { "type": "TASK_STARTED",   "detail": "Agent: bugfix-assistant" },
    { "type": "LLM_RESPONSE",   "detail": "{\"thought\": \"I should search for UserService...\", ...}" },
    { "type": "THOUGHT",        "detail": "I should search for UserService to find the relevant file" },
    { "type": "POLICY_CHECK",   "detail": "tool=search_code decision=ALLOW" },
    { "type": "TOOL_EXECUTED",  "detail": "tool=search_code result=Found 3 match(es)..." },
    { "type": "THOUGHT",        "detail": "Let me read UserService.java to examine the code" },
    { "type": "POLICY_CHECK",   "detail": "tool=read_file decision=ALLOW" },
    { "type": "TOOL_EXECUTED",  "detail": "tool=read_file result=package com.example;..." },
    { "type": "TASK_COMPLETED", "detail": "The bug is in UserService.getUserCity()..." }
  ]
}
```

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   REST API Layer                     │
│              AgentController (POST /api/agent/task)  │
├─────────────────────────────────────────────────────┤
│                  Agent Runtime                       │
│         execute loop: think → act → observe          │
├──────────────────┬──────────────────────────────────┤
│   Tool Registry  │         Policy Engine             │
│  ┌─────────────┐ │  ┌────────────────────────────┐  │
│  │  read_file   │ │  │  ALLOW / DENY / APPROVAL   │  │
│  │  search_code │ │  │  per-tool, per-agent rules  │  │
│  │  run_command │ │  │  risk-level awareness       │  │
│  └─────────────┘ │  └────────────────────────────┘  │
├──────────────────┴──────────────────────────────────┤
│               Spring AI (ChatClient)                 │
│            OpenAI / Anthropic / Ollama               │
└─────────────────────────────────────────────────────┘
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
│       └── spring/         # JavaclawAutoConfiguration, JavaclawProperties
├── javaclaw-demo/          # Spring Boot demo app
│   ├── sample-repo/        # Sample Java code with intentional bugs
│   └── com.javaclaw.demo   # DemoAgentConfig, AgentController
└── docs/
    └── ARCHITECTURE.md
```

## Configuration

```yaml
javaclaw:
  max-steps: 10                    # Max agent loop iterations
  tools:
    base-directory: ./sample-repo  # Sandbox for file tools

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
```

## License

[Apache License 2.0](LICENSE)
