package com.programacho.agenttracehandoff;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "handoff")
public record AgentTraceHandoffProperties(
        String agent,
        String callerName,
        String prompt) {
}
