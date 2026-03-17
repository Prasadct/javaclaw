package com.javaclaw.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AgentTask {

    private final String id;
    private final String goal;
    private TaskStatus status;
    private String result;
    private final List<AuditEvent> auditTrail = new ArrayList<>();

    public AgentTask(String goal) {
        this.id = UUID.randomUUID().toString();
        this.goal = goal;
        this.status = TaskStatus.CREATED;
    }

    public String getId() {
        return id;
    }

    public String getGoal() {
        return goal;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public List<AuditEvent> getAuditTrail() {
        return Collections.unmodifiableList(auditTrail);
    }

    public void addAuditEvent(AuditEvent event) {
        this.auditTrail.add(event);
    }
}
