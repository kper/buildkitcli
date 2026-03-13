package io.github.kper.buildkit.internal.filesync;

import static org.assertj.core.api.Assertions.assertThat;

import fsutil.types.Wire;
import io.github.kper.buildkit.internal.session.MetadataCapturingInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import moby.filesync.v1.FileSyncGrpc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSyncServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void streamsStatsAndFileContentsInLexicographicOrder() throws Exception {
        Path contextDir = Files.createDirectory(tempDir.resolve("context"));
        Files.writeString(contextDir.resolve("b.txt"), "b");
        Files.writeString(contextDir.resolve("a.txt"), "a");

        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        Server server = NettyServerBuilder.forAddress(address)
                .addService(ServerInterceptors.intercept(
                        new FileSyncService(Map.of("context", contextDir)),
                        new MetadataCapturingInterceptor()))
                .build()
                .start();

        InetSocketAddress boundAddress = (InetSocketAddress) server.getListenSockets().getFirst();
        ManagedChannel channel = NettyChannelBuilder.forAddress(boundAddress).usePlaintext().build();

        try {
            Metadata headers = new Metadata();
            headers.put(Metadata.Key.of("dir-name", Metadata.ASCII_STRING_MARSHALLER), "context");

            List<String> statPaths = new ArrayList<>();
            StringBuilder fileContents = new StringBuilder();
            CompletableFuture<Void> completed = new CompletableFuture<>();
            CountDownLatch sawAFile = new CountDownLatch(1);
            CountDownLatch sawStatsEnd = new CountDownLatch(1);
            CountDownLatch sawDataEnd = new CountDownLatch(1);
            CountDownLatch sawFin = new CountDownLatch(1);

            StreamObserver<Wire.Packet> requestObserver = FileSyncGrpc.newStub(channel)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                    .diffCopy(new StreamObserver<>() {
                        @Override
                        public void onNext(Wire.Packet value) {
                            switch (value.getType()) {
                                case PACKET_STAT -> {
                                    if (!value.hasStat()) {
                                        sawStatsEnd.countDown();
                                        return;
                                    }
                                    statPaths.add(value.getStat().getPath());
                                    if ("a.txt".equals(value.getStat().getPath())) {
                                        sawAFile.countDown();
                                    }
                                }
                                case PACKET_DATA -> {
                                    if (value.getData().isEmpty()) {
                                        sawDataEnd.countDown();
                                    } else {
                                        fileContents.append(value.getData().toStringUtf8());
                                    }
                                }
                                case PACKET_FIN -> {
                                    sawFin.countDown();
                                    completed.complete(null);
                                }
                                default -> {
                                }
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            completed.completeExceptionally(t);
                        }

                        @Override
                        public void onCompleted() {
                            completed.complete(null);
                        }
                    });

            assertThat(sawAFile.await(5, TimeUnit.SECONDS)).isTrue();
            requestObserver.onNext(Wire.Packet.newBuilder()
                    .setType(Wire.Packet.PacketType.PACKET_REQ)
                    .setID(0)
                    .build());
            assertThat(sawStatsEnd.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(sawDataEnd.await(5, TimeUnit.SECONDS)).isTrue();
            requestObserver.onNext(Wire.Packet.newBuilder()
                    .setType(Wire.Packet.PacketType.PACKET_FIN)
                    .build());
            assertThat(sawFin.await(5, TimeUnit.SECONDS)).isTrue();
            completed.get(5, TimeUnit.SECONDS);
            requestObserver.onCompleted();

            assertThat(statPaths).containsExactly("a.txt", "b.txt");
            assertThat(fileContents.toString()).isEqualTo("a");
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
