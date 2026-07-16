package com.programacho.agenttracehandoff;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Agent {

    CLAUDE {
        @Override
        List<String> argv(String prompt) {
            return List.of("claude", "-p", prompt);
        }

        @Override
        Map<String, String> env(TraceHandoff handoff) {
            return Map.of(
                    "TRACEPARENT", handoff.traceparent(),
                    "OTEL_RESOURCE_ATTRIBUTES", "caller.name=" + handoff.callerName()
            );
        }
    },

    CODEX {
        @Override
        List<String> argv(String prompt) {
            if (System.getProperty("os.name").startsWith("Windows")) {
                return List.of("cmd", "/c", "codex", "exec", prompt);
            }
            return List.of("codex", "exec", prompt);
        }

        @Override
        Map<String, String> env(TraceHandoff handoff) {
            return Map.of(
                    "OTEL_RESOURCE_ATTRIBUTES", "caller.name=" + handoff.callerName(),
                    "CODEX_HOME", Path.of("../.codex").toAbsolutePath().normalize().toString()
            );
        }
    };

    abstract List<String> argv(String prompt);

    abstract Map<String, String> env(TraceHandoff handoff);

    static Agent from(String name) {
        return Stream.of(values())
                .filter(a -> a.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "handoff.agent='" + name + "' is not supported yet; implemented: " + supported()
                ));
    }

    private static String supported() {
        return Stream.of(values()).map(a -> a.name().toLowerCase()).collect(Collectors.joining(", "));
    }
}
