package io.github.kper.buildkitcli.internal.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Iterables;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import moby.buildkit.v1.ControlGrpc;
import moby.buildkit.v1.ControlOuterClass;
import org.junit.jupiter.api.Test;

class BuildkitSessionBridgeTest {
    @Test
    void proxiesBytesAndAttachesSessionHeaders() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        AtomicReference<Metadata> capturedHeaders = new AtomicReference<>();
        AtomicReference<StreamObserver<ControlOuterClass.BytesMessage>> responseObserverRef = new AtomicReference<>();
        CountDownLatch receivedFromSocket = new CountDownLatch(1);
        AtomicReference<String> receivedPayload = new AtomicReference<>();

        ServerInterceptor headerCapturingInterceptor = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> io.grpc.ServerCall.Listener<ReqT> interceptCall(
                    io.grpc.ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    io.grpc.ServerCallHandler<ReqT, RespT> next) {
                capturedHeaders.set(headers);
                return next.startCall(call, headers);
            }
        };

        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerInterceptors.intercept(new ControlGrpc.ControlImplBase() {
                    @Override
                    public StreamObserver<ControlOuterClass.BytesMessage> session(
                            StreamObserver<ControlOuterClass.BytesMessage> responseObserver) {
                        responseObserverRef.set(responseObserver);
                        return new StreamObserver<>() {
                            @Override
                            public void onNext(ControlOuterClass.BytesMessage value) {
                                receivedPayload.set(value.getData().toStringUtf8());
                                receivedFromSocket.countDown();
                            }

                            @Override
                            public void onError(Throwable t) {}

                            @Override
                            public void onCompleted() {}
                        };
                    }
                }, headerCapturingInterceptor))
                .build()
                .start();

        try (ServerSocket loopbackServer = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            try {
            CountDownLatch clientConnected = new CountDownLatch(1);
            CountDownLatch receivedFromBridge = new CountDownLatch(1);
            AtomicReference<String> socketMessage = new AtomicReference<>();

            Thread loopbackThread = Thread.ofVirtual().start(() -> {
                try (Socket accepted = loopbackServer.accept()) {
                    clientConnected.countDown();
                    accepted.getOutputStream().write("ping".getBytes());
                    accepted.getOutputStream().flush();

                    byte[] buffer = new byte[4];
                    int read = accepted.getInputStream().read(buffer);
                    socketMessage.set(new String(buffer, 0, read));
                    receivedFromBridge.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            try (BuildkitSessionBridge ignored = BuildkitSessionBridge.start(
                    ControlGrpc.newStub(channel),
                    "session-id",
                    "",
                    List.of("/grpc.health.v1.Health/Check", "/moby.filesync.v1.FileSync/DiffCopy"),
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), loopbackServer.getLocalPort()))) {
                assertThat(clientConnected.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(receivedFromSocket.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(receivedPayload.get()).isEqualTo("ping");

                responseObserverRef.get().onNext(ControlOuterClass.BytesMessage.newBuilder()
                        .setData(com.google.protobuf.ByteString.copyFromUtf8("pong"))
                        .build());

                assertThat(receivedFromBridge.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(socketMessage.get()).isEqualTo("pong");

                Metadata headers = capturedHeaders.get();
                assertThat(headers.get(Metadata.Key.of("x-docker-expose-session-uuid", Metadata.ASCII_STRING_MARSHALLER)))
                        .isEqualTo("session-id");
                assertThat(Iterables.get(headers.getAll(
                                Metadata.Key.of("x-docker-expose-session-grpc-method", Metadata.ASCII_STRING_MARSHALLER)), 0))
                        .isEqualTo("/grpc.health.v1.Health/Check");
            }

            loopbackThread.join();
            } finally {
                channel.shutdownNow();
                channel.awaitTermination(5, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
