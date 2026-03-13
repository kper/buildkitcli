package io.github.kper.buildkit.internal.session;

import io.grpc.Context;
import io.grpc.Metadata;

public final class SessionMetadata {
    private static final Context.Key<Metadata> KEY = Context.key("buildkit-session-metadata");

    private SessionMetadata() {}

    public static Context withMetadata(Context context, Metadata metadata) {
        return context.withValue(KEY, metadata);
    }

    public static Metadata current() {
        return KEY.get();
    }
}
