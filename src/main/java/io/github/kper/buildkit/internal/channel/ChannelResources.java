package io.github.kper.buildkit.internal.channel;

import io.grpc.ManagedChannel;
import io.netty.channel.EventLoopGroup;
import java.util.concurrent.TimeUnit;

public final class ChannelResources implements AutoCloseable {
    private final ManagedChannel channel;
    private final EventLoopGroup eventLoopGroup;

    public ChannelResources(ManagedChannel channel, EventLoopGroup eventLoopGroup) {
        this.channel = channel;
        this.eventLoopGroup = eventLoopGroup;
    }

    public ManagedChannel channel() {
        return channel;
    }

    @Override
    public void close() throws InterruptedException {
        channel.shutdownNow();
        channel.awaitTermination(5, TimeUnit.SECONDS);
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        }
    }
}
