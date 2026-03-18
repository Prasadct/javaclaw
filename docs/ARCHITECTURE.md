# Javaclaw Architecture

## System layers

```
┌─────────────────────────────────────────────────────────┐
│  1. API Layer                                           │
│     AgentController — REST endpoint for task submission  │
│     POST /api/agent/task  { "goal": "..." }             │
├─────────────────────────────────────────────────────────┤
│  2. Runtime Layer                                       │
│     AgentRuntime — orchestrates the think/act/observe    │
│     loop with configurable max-steps                    │
├────────────────────────┬────────────────────────────────┤
│  3a. Tool Layer        │  3b. Policy Layer              │
│     ToolRegistry       │     PolicyEngine               │
│     ToolDefinition     │     PolicyDecision             │
│     Built-in tools:    │     (ALLOW / DENY /            │
│     - read_file        │      REQUIRE_APPROVAL)         │
│     - search_code      │     RiskLevel per tool         │
│     - run_command      │     Per-agent, per-tool rules  │
├────────────────────────┴────────────────────────────────┤
│  4. Model Layer                                         │
│     AgentDefinition — name, system prompt, allowed tools│
│     AgentTask — id, goal, status, result, audit trail   │
│     AuditEvent — timestamp, type, detail                │
├─────────────────────────────────────────────────────────┤
│  5. Spring AI Integration                               │
│     ChatClient — abstraction over LLM providers         │
│     Auto-configuration via JavaclawAutoConfiguration    │
│     Configuration via JavaclawProperties (application.yml) │
└─────────────────────────────────────────────────────────┘
```

## Core execution loop

The `AgentRuntime.execute()` method implements a ReAct-style agent loop:

```
 START
   │
   ▼
 Create AgentTask (status: CREATED → RUNNING)
   │
   ▼
 Build system prompt:
   agent.systemPrompt + tool descriptions + JSON format instructions
   │
   ▼
┌──────────────────────────────────────────┐
│  LOOP (max-steps iterations)             │
│                                          │
│  1. Send context to LLM via ChatClient   │
│     ↓                                    │
│  2. Parse JSON response                  │
│     { thought, action, input }           │
│     ↓                                    │
│  3. If action = "finish"                 │
│       → set result, status = COMPLETED   │
│       → RETURN                           │
│     ↓                                    │
│  4. Look up tool in ToolRegistry         │
│     ↓                                    │
│  5. Check PolicyEngine                   │
│       DENY    → status = FAILED, RETURN  │
│       REQUIRE_APPROVAL → pause, approve  │
│       ALLOW   → continue                 │
│     ↓                                    │
│  6. Execute tool                         │
│     ↓                                    │
│  7. Add tool result to conversation      │
│  8. Record audit events                  │
│     ↓                                    │
│  (next iteration)                        │
└──────────────────────────────────────────┘
   │
   ▼ (max iterations reached)
 status = FAILED, RETURN
```

Every step — LLM response, thought, policy check, tool execution, errors — is recorded as an `AuditEvent` in the task's audit trail.

## Policy engine

The `PolicyEngine` interface has a single method:

```java
PolicyDecision evaluate(AgentDefinition agent, String toolName, String input)
```

It returns one of:

| Decision | Behavior |
|---|---|
| `ALLOW` | Tool executes immediately |
| `DENY` | Task fails with explanation, tool never runs |
| `REQUIRE_APPROVAL` | Task pauses for human approval before executing |

The default auto-configured policy allows everything. Override it by defining your own `PolicyEngine` bean:

```java
@Bean
public PolicyEngine policyEngine() {
    return (agent, toolName, input) -> {
        if ("run_command".equals(toolName)) {
            return PolicyDecision.REQUIRE_APPROVAL;
        }
        if (!agent.allowedTools().contains(toolName)) {
            return PolicyDecision.DENY;
        }
        return PolicyDecision.ALLOW;
    };
}
```

Each `ToolDefinition` also carries a `RiskLevel` (LOW, MEDIUM, HIGH) that your policy engine can use for decisions.

## How to add custom tools

### 1. Create the tool class

```java
public class DatabaseQueryTool {

    private final DataSource dataSource;

    public DatabaseQueryTool(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public ToolDefinition definition() {
        return new ToolDefinition(
            "query_db",
            "Runs a read-only SQL query. Input: the SQL SELECT statement.",
            RiskLevel.MEDIUM,
            this::execute
        );
    }

    private String execute(String input) {
        // Validate input is SELECT only
        // Execute query
        // Return formatted results
    }
}
```

### 2. Register as a Spring bean

```java
@Bean
public ToolDefinition databaseQueryTool(DataSource dataSource) {
    return new DatabaseQueryTool(dataSource).definition();
}
```

The `JavaclawAutoConfiguration` will automatically discover all `ToolDefinition` beans in the application context and register them in the `ToolRegistry`.

### 3. Update your policy

Add rules for the new tool in your `PolicyEngine` bean. Consider the tool's risk level — a database query tool should probably require approval or be restricted to SELECT statements.

### 4. Allow it in your agent

Include the tool name in the agent's `allowedTools` list:

```java
new AgentDefinition(
    "data-analyst",
    "You analyze data using SQL queries...",
    List.of("query_db", "read_file")
);
```
