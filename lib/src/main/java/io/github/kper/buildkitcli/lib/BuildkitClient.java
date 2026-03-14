package io.github.kper.buildkitcli.lib;

import com.google.protobuf.ByteString;
import io.github.kper.buildkitcli.lib.internal.ProtoTimestamps;
import io.github.kper.buildkitcli.lib.internal.channel.BuildkitChannelFactory;
import io.github.kper.buildkitcli.lib.internal.channel.ChannelResources;
import io.github.kper.buildkitcli.lib.internal.session.BuildkitSessionBridge;
import io.github.kper.buildkitcli.lib.internal.session.LoopbackSessionServer;
import io.github.kper.buildkitcli.lib.internal.solve.SolveRequestFactory;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import moby.buildkit.v1.ControlGrpc;
import moby.buildkit.v1.ControlOuterClass;

/**
 * Client implementation which connects to the buildkit daemon.
 */
public final class BuildkitClient implements AutoCloseable {
    private static final long STATUS_QUIET_PERIOD_NANOS = TimeUnit.SECONDS.toNanos(5);

    private final BuildkitConnectionConfig connectionConfig;
    private final ChannelResources channelResources;
    private final ControlGrpc.ControlBlockingStub blockingStub;
    private final ControlGrpc.ControlStub asyncStub;

    /**
     * Constructor which initiates the connection.
     */
    public BuildkitClient(BuildkitConnectionConfig connectionConfig) throws IOException {
        this.connectionConfig = Objects.requireNonNull(connectionConfig, "connectionConfig");
        this.channelResources = BuildkitChannelFactory.create(connectionConfig);
        this.blockingStub = ControlGrpc.newBlockingStub(channelResources.channel());
        this.asyncStub = ControlGrpc.newStub(channelResources.channel());
    }

    /**
     * Builds an image.
     *
     * @param request  has the payload for the daemon to build the image.
     * @param listener reports the progress in the client.
     * @return the build result.
     * @throws BuildkitException when an build error occurs.
     */
    public BuildResult buildImage(DockerfileBuildRequest request, BuildProgressListener listener)
            throws BuildkitException, InterruptedException {
        Objects.requireNonNull(request, "request");
        BuildProgressListener effectiveListener = listener == null ? BuildProgressListener.NOOP : listener;
        String ref = newId();
        String sessionId = newId();
        Path exportedArchive = request.outputMode().usesSessionExporter()
                ? createExportArchivePath(request.outputMode())
                : null;

        Map<String, Path> sharedDirs = Map.of(
                "context", request.contextDir(),
                "dockerfile", request.dockerfile().getParent());

        try (LoopbackSessionServer sessionServer = LoopbackSessionServer.start(sharedDirs, exportedArchive);
             BuildkitSessionBridge ignored = BuildkitSessionBridge.start(
                     asyncStub(), sessionId, "", sessionServer.exposedMethods(), sessionServer.address());
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            Context.CancellableContext statusContext = Context.current().withCancellation();
            AtomicLong lastStatusActivity = new AtomicLong(System.nanoTime());

            CompletableFuture<Void> statusFuture = CompletableFuture.runAsync(
                    () -> watchStatus(statusContext, ref, effectiveListener, lastStatusActivity),
                    executor);

            ControlOuterClass.SolveResponse response;
            try {
                response = blockingStub().solve(SolveRequestFactory.create(ref, sessionId, request));
            } catch (StatusRuntimeException e) {
                waitForStatusQuiescence(statusContext, lastStatusActivity, statusFuture);
                deleteIfExists(exportedArchive);
                throw new BuildkitException("BuildKit solve failed: " + e.getStatus(), e);
            }

            waitForStatusQuiescence(statusContext, lastStatusActivity, statusFuture);
            verifyExportedArchive(exportedArchive);
            return toResult(ref, response, exportedArchive);
        } catch (IOException e) {
            deleteIfExists(exportedArchive);
            throw new BuildkitException("Failed to start BuildKit session bridge", e);
        } catch (ExecutionException e) {
            deleteIfExists(exportedArchive);
            throw new BuildkitException("BuildKit status stream failed", e.getCause());
        }
    }

    @Override
    public void close() throws InterruptedException {
        channelResources.close();
    }

    private ControlGrpc.ControlBlockingStub blockingStub() {
        return blockingStub.withDeadlineAfter(connectionConfig.timeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private ControlGrpc.ControlStub asyncStub() {
        return asyncStub.withDeadlineAfter(connectionConfig.timeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void watchStatus(
            Context.CancellableContext statusContext,
            String ref,
            BuildProgressListener listener,
            AtomicLong lastStatusActivity) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            try {
                statusContext.run(() -> {
                    var responses = blockingStub().status(ControlOuterClass.StatusRequest.newBuilder().setRef(ref).build());
                    responses.forEachRemaining(response -> {
                        lastStatusActivity.set(System.nanoTime());
                        dispatchStatus(response, listener);
                    });
                });
                return;
            } catch (StatusRuntimeException e) {
                if (statusContext.isCancelled() || e.getStatus().getCode() == Status.Code.CANCELLED) {
                    return;
                }
                if (e.getStatus().getCode() == Status.Code.NOT_FOUND && System.nanoTime() < deadline) {
                    sleepUnchecked(100);
                    continue;
                }
                throw e;
            }
        }
    }

    private void waitForStatusQuiescence(
            Context.CancellableContext statusContext,
            AtomicLong lastStatusActivity,
            CompletableFuture<Void> statusFuture)
            throws InterruptedException, ExecutionException {
        while (!statusFuture.isDone()) {
            if (System.nanoTime() - lastStatusActivity.get() >= STATUS_QUIET_PERIOD_NANOS) {
                statusContext.cancel(null);
                break;
            }
            Thread.sleep(100);
        }
        statusContext.cancel(null);
        statusFuture.get();
    }

    private static void dispatchStatus(ControlOuterClass.StatusResponse response, BuildProgressListener listener) {
        response.getVertexesList().forEach(vertex -> listener.onVertex(new BuildVertex(
                vertex.getDigest(),
                vertex.getInputsList(),
                vertex.getName(),
                vertex.getCached(),
                ProtoTimestamps.toInstant(vertex.getStarted()),
                ProtoTimestamps.toInstant(vertex.getCompleted()),
                vertex.getError())));

        response.getStatusesList().forEach(status -> listener.onVertexStatus(new BuildVertexStatus(
                status.getID(),
                status.getVertex(),
                status.getName(),
                status.getCurrent(),
                status.getTotal(),
                ProtoTimestamps.toInstant(status.getTimestamp()),
                ProtoTimestamps.toInstant(status.getStarted()),
                ProtoTimestamps.toInstant(status.getCompleted()))));

        response.getLogsList().forEach(log -> listener.onLog(new BuildLog(
                log.getVertex(),
                ProtoTimestamps.toInstant(log.getTimestamp()),
                log.getStream(),
                log.getMsg().toByteArray())));

        response.getWarningsList().forEach(warning -> {
            List<String> details = new ArrayList<>();
            for (ByteString detail : warning.getDetailList()) {
                details.add(detail.toString(StandardCharsets.UTF_8));
            }
            listener.onWarning(new BuildWarning(
                    warning.getVertex(),
                    warning.getLevel(),
                    warning.getShort().toString(StandardCharsets.UTF_8),
                    details,
                    warning.getUrl()));
        });
    }

    private static BuildResult toResult(String ref, ControlOuterClass.SolveResponse response, Path exportedArchive) {
        Map<String, String> exporterResponse = new LinkedHashMap<>(response.getExporterResponseMap());
        return new BuildResult(
                ref,
                exporterResponse,
                exporterResponse.get("containerimage.digest"),
                exporterResponse.get("containerimage.descriptor"),
                exportedArchive);
    }

    private static Path createExportArchivePath(BuildOutputMode outputMode) throws BuildkitException {
        try {
            return Files.createTempFile("buildkit-export-", "." + outputMode.name().toLowerCase() + ".tar");
        } catch (IOException e) {
            throw new BuildkitException("Failed to create exporter output file", e);
        }
    }

    private static void verifyExportedArchive(Path exportedArchive) throws BuildkitException {
        if (exportedArchive == null) {
            return;
        }
        try {
            if (!Files.isRegularFile(exportedArchive) || Files.size(exportedArchive) == 0) {
                throw new BuildkitException("BuildKit did not produce an export archive");
            }
        } catch (IOException e) {
            throw new BuildkitException("Failed to verify exported archive", e);
        }
    }

    private static void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static void sleepUnchecked(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
