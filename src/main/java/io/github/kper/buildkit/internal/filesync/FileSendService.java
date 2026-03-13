package io.github.kper.buildkit.internal.filesync;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import moby.filesync.v1.FileSendGrpc;
import moby.filesync.v1.Filesync;

public final class FileSendService extends FileSendGrpc.FileSendImplBase {
    private final Path targetFile;

    public FileSendService(Path targetFile) {
        this.targetFile = targetFile;
    }

    @Override
    public StreamObserver<Filesync.BytesMessage> diffCopy(StreamObserver<Filesync.BytesMessage> responseObserver) {
        try {
            OutputStream outputStream = Files.newOutputStream(
                    targetFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            return new Receiver(outputStream, responseObserver);
        } catch (IOException e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("failed to open export target: " + targetFile)
                    .withCause(e)
                    .asRuntimeException());
            return new NoopStreamObserver();
        }
    }

    private static final class Receiver implements StreamObserver<Filesync.BytesMessage> {
        private final OutputStream outputStream;
        private final StreamObserver<Filesync.BytesMessage> responseObserver;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Receiver(OutputStream outputStream, StreamObserver<Filesync.BytesMessage> responseObserver) {
            this.outputStream = outputStream;
            this.responseObserver = responseObserver;
        }

        @Override
        public void onNext(Filesync.BytesMessage value) {
            if (closed.get()) {
                return;
            }
            try {
                value.getData().writeTo(outputStream);
            } catch (IOException e) {
                fail(e);
            }
        }

        @Override
        public void onError(Throwable t) {
            fail(t);
        }

        @Override
        public void onCompleted() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                outputStream.close();
                responseObserver.onCompleted();
            } catch (IOException e) {
                responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
            }
        }

        private void fail(Throwable throwable) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                outputStream.close();
            } catch (IOException ignored) {
            }
            responseObserver.onError(Status.INTERNAL.withCause(throwable).asRuntimeException());
        }
    }

    private static final class NoopStreamObserver implements StreamObserver<Filesync.BytesMessage> {
        @Override
        public void onNext(Filesync.BytesMessage value) {}

        @Override
        public void onError(Throwable t) {}

        @Override
        public void onCompleted() {}
    }
}
