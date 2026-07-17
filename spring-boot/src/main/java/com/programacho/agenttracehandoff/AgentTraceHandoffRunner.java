package com.programacho.agenttracehandoff;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AgentTraceHandoffRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceHandoffRunner.class);

    private final AgentTraceHandoffProperties properties;
    private final Tracer tracer;
    private final Propagator propagator;

    public AgentTraceHandoffRunner(AgentTraceHandoffProperties properties, Tracer tracer, Propagator propagator) {
        this.properties = properties;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public void run(String... args) throws Exception {
        Agent agent = Agent.from(properties.agent());
        List<String> argv = agent.argv(properties.prompt());
        File agentWorkingDir = Path.of("..").toAbsolutePath().normalize().toFile();

        Span span = tracer.nextSpan().name("handoff").start();

        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            ProcessBuilder processBuilder = new ProcessBuilder(argv);
            processBuilder.directory(agentWorkingDir);

            processBuilder.environment().putAll(agent.env(new TraceHandoff(
                    traceparent(span),
                    properties.callerName()
            )));
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

            log.info("launching {} in {} with prompt \"{}\"", agent.name().toLowerCase(), agentWorkingDir, properties.prompt());
            log.info("find this run in Elasticsearch: trace.id={}, labels.caller_name={}", span.context().traceId(), properties.callerName());

            Process process = processBuilder.start();
            process.getOutputStream().close();
            String response = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(agent.name().toLowerCase() + " exited with code " + exitCode);
            }

            log.info("{} responded: \"{}\"", agent.name().toLowerCase(), response);
            log.info("{} exited with code {} — its telemetry flushes on exit and joins trace.id={}", agent.name().toLowerCase(), exitCode, span.context().traceId());
        } finally {
            span.end();
        }
    }

    private String traceparent(Span span) {
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(span.context(), carrier, Map::put);
        return carrier.get("traceparent");
    }

}
