package io.github.kper.buildkit;

import java.time.Instant;

public record BuildVertexStatus(
        String id,
        String vertex,
        String name,
        long current,
        long total,
        Instant timestamp,
        Instant started,
        Instant completed) {}
