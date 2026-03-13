package io.github.kper.buildkitcli.lib.internal.session;

import com.google.protobuf.ByteString;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import moby.buildkit.v1.ControlGrpc;
import moby.buildkit.v1.ControlOuterClass;

public final class BuildkitSessionBridge implements AutoCloseable {
    private static final Metadata.Key<String> SESSION_ID =
            Metadata.Key.of("x-docker-expose-session-uuid", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SHARED_KEY =
            Metadata.Key.of("x-docker-expose-session-sharedkey", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> METHOD =
            Metadata.Key.of("x-docker-expose-session-grpc-method", Metadata.ASCII_STRING_MARSHALLER);

    private final Socket socket;
    private final ExecutorService executor;
    private final StreamObserver<ControlOuterClass.BytesMessage> requestObserver;
    private final CountDownLatch finished = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private BuildkitSessionBridge(
            Socket socket,
            ExecutorService executor,
            StreamObserver<ControlOuterClass.BytesMessage> requestObserver) {
        this.socket = socket;
        this.executor = executor;
        this.requestObserver = requestObserver;
    }

    public static BuildkitSessionBridge start(
            ControlGrpc.ControlStub baseStub,
            String sessionId,
            String sharedKey,
            List<String> methods,
            InetSocketAddress targetAddress)
            throws IOException {
        Metadata metadata = new Metadata();
        metadata.put(SESSION_ID, sessionId);
        metadata.put(SHARED_KEY, sharedKey == null ? "" : sharedKey);
        methods.forEach(method -> metadata.put(METHOD, method));

        Socket socket = new Socket();
        socket.connect(targetAddress);
        socket.setTcpNoDelay(true);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        OutputStream outputStream = socket.getOutputStream();

        StreamObserver<ControlOuterClass.BytesMessage> requestObserver =
                baseStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                        .session(new StreamObserver<ControlOuterClass.BytesMessage>() {
                    @Override
                    public void onNext(ControlOuterClass.BytesMessage value) {
                        try {
                            outputStream.write(value.getData().toByteArray());
                            outputStream.flush();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        closeQuietly(socket);
                    }

                    @Override
                    public void onCompleted() {
                        closeQuietly(socket);
                    }
                });

        BuildkitSessionBridge bridge = new BuildkitSessionBridge(socket, executor, requestObserver);
        bridge.startSocketReader();
        return bridge;
    }

    private void startSocketReader() throws IOException {
        InputStream inputStream = socket.getInputStream();
        executor.submit(() -> {
            byte[] buffer = new byte[32 * 1024];
            try {
                int read;
                while (!closed.get() && (read = inputStream.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    requestObserver.onNext(ControlOuterClass.BytesMessage.newBuilder()
                            .setData(ByteString.copyFrom(buffer, 0, read))
                            .build());
                }
                requestObserver.onCompleted();
            } catch (Throwable t) {
                if (!closed.get()) {
                    requestObserver.onError(t);
                }
            } finally {
                finished.countDown();
            }
        });
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(socket);
        executor.shutdownNow();
        try {
            finished.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
