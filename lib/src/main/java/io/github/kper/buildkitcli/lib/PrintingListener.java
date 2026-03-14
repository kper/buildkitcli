package io.github.kper.buildkitcli.lib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrintingListener implements BuildProgressListener {
    private final Logger logger = LoggerFactory.getLogger(PrintingListener.class);

    @Override
    public void onVertex(BuildVertex vertex) {
        if (!vertex.name().isBlank()) {
            logger.atDebug().log(vertex.name());
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
