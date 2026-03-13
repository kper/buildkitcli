package io.github.kper.buildkitcli.lib;

import java.time.Instant;
import java.util.List;

public record BuildVertex(
        String digest,
        List<String> inputs,
        String name,
        boolean cached,
        Instant started,
        Instant completed,
        String error) {

    public BuildVertex {
        inputs = List.copyOf(inputs);
    }
}
