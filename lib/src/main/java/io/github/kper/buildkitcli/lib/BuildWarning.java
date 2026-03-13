package io.github.kper.buildkitcli.lib;

import java.util.List;

public record BuildWarning(String vertex, long level, String shortMessage, List<String> details, String url) {
    public BuildWarning {
        details = List.copyOf(details);
    }
}
