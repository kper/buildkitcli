package io.github.kper.buildkitcli.cli;

import io.github.kper.buildkitcli.lib.BuildLog;
import io.github.kper.buildkitcli.lib.BuildProgressListener;
import io.github.kper.buildkitcli.lib.BuildVertex;
import io.github.kper.buildkitcli.lib.BuildWarning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrintingListener implements BuildProgressListener {
    private final Logger logger = LoggerFactory.getLogger(PrintingListener.class);

    @Override
    public void onVertex(BuildVertex vertex) {
        if (!vertex.name().isBlank()) {
            logger.atError().log(vertex.name());
        }
    }

    @Override
    public void onLog(BuildLog log) {
        String message = log.utf8Message();
        if (!message.isBlank()) {
            logger.atInfo().log(message);
        }
    }

    @Override
    public void onWarning(BuildWarning warning) {
        logger.atError().log("warning: " + warning.shortMessage());
    }
}
