package io.github.kper.buildkit.internal.session;

import io.github.kper.buildkit.internal.filesync.FileSendService;
import io.github.kper.buildkit.internal.filesync.FileSyncService;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import moby.filesync.v1.FileSendGrpc;
import moby.filesync.v1.FileSyncGrpc;

public final class LoopbackSessionServer implements AutoCloseable {
    private final Server server;
    private final InetSocketAddress address;

    private LoopbackSessionServer(Server server, InetSocketAddress address) {
        this.server = server;
        this.address = address;
    }

    public static LoopbackSessionServer start(Map<String, Path> sharedDirs, Path exportFile) throws IOException {
        InetSocketAddress requestedAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        HealthStatusManager health = new HealthStatusManager();
        health.setStatus("", HealthCheckResponse.ServingStatus.SERVING);
        health.setStatus(HealthGrpc.SERVICE_NAME, HealthCheckResponse.ServingStatus.SERVING);
        health.setStatus(FileSyncGrpc.SERVICE_NAME, HealthCheckResponse.ServingStatus.SERVING);
        if (exportFile != null) {
            health.setStatus(FileSendGrpc.SERVICE_NAME, HealthCheckResponse.ServingStatus.SERVING);
        }

        MetadataCapturingInterceptor metadataInterceptor = new MetadataCapturingInterceptor();
        NettyServerBuilder serverBuilder = NettyServerBuilder.forAddress(requestedAddress)
                .addService(ServerInterceptors.intercept(health.getHealthService(), metadataInterceptor))
                .addService(ServerInterceptors.intercept(new FileSyncService(sharedDirs), metadataInterceptor));
        if (exportFile != null) {
            serverBuilder.addService(ServerInterceptors.intercept(new FileSendService(exportFile), metadataInterceptor));
        }
        Server server = serverBuilder.build().start();
        InetSocketAddress boundAddress = (InetSocketAddress) server.getListenSockets().getFirst();
        return new LoopbackSessionServer(server, boundAddress);
    }

    public InetSocketAddress address() {
        return address;
    }

    public List<String> exposedMethods() {
        return List.of(
                "/" + HealthGrpc.SERVICE_NAME + "/Check",
                "/" + FileSyncGrpc.SERVICE_NAME + "/DiffCopy",
                "/" + FileSendGrpc.SERVICE_NAME + "/DiffCopy");
    }

    @Override
    public void close() {
        server.shutdownNow();
        try {
            server.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
