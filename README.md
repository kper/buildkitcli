# buildkit-java-client

Vibecoded pure-java gRPC client for BuildKit Dockerfile builds with a local build context.

## What it does

- Connects to `buildkitd` over `unix://` or `tcp://`
- Builds Docker images through BuildKit's `dockerfile.v0` frontend
- Streams a local `context` directory and `dockerfile` directory through the BuildKit session tunnel
- Exposes build progress, logs, warnings, and final exporter metadata

Current scope is intentionally small:

- Supported exporters: `type=image`, `type=docker` (for local `docker load`)
- Supported auth mode: anonymous/public registries only
- Supported local context file types: regular files, directories, symlinks

## Build

```bash
gradle build
```

## Library usage

```java
import io.github.kper.buildkit.BuildProgressListener;
import io.github.kper.buildkit.BuildResult;
import io.github.kper.buildkit.BuildkitClient;
import io.github.kper.buildkit.BuildkitConnectionConfig;
import io.github.kper.buildkit.DockerfileBuildRequest;

import java.net.URI;
import java.time.Duration;
import java.nio.file.Path;

BuildkitConnectionConfig config = new BuildkitConnectionConfig(
        URI.create("unix:///run/buildkit/buildkitd.sock"),
        Duration.ofMinutes(10),
        null);

DockerfileBuildRequest request = DockerfileBuildRequest.builder(
                Path.of("/workspace/app"),
                Path.of("/workspace/app/Dockerfile"),
                "example.com/demo:latest")
        .buildArg("VERSION", "1.0.0")
        .build();

BuildkitClient client = new BuildkitClient(config);
BuildResult result = client.buildImage(request, BuildProgressListener.NOOP);

System.out.println(result.imageDigest());
```

## CLI example

```bash
gradle run --args='--addr unix:///run/buildkit/buildkitd.sock --context /tmp/app --dockerfile /tmp/app/Dockerfile --image example.com/demo:latest'
```

```bash
gradle run --args='--addr tcp://localhost:1234 --context /tmp/app --dockerfile /tmp/app/Dockerfile --image example.com/demo:latest'
```

To load the result into your local Docker daemon instead of exporting it as a BuildKit image:

```bash
gradle run --args='--addr unix:///run/buildkit/buildkitd.sock --context /tmp/app --dockerfile /tmp/app/Dockerfile --image local/demo:latest --load'
```

## Tests

`gradle test` runs:

- request-mapping unit tests
- session-bridge unit tests
- `FileSync` protocol tests
- container-backed integration tests when Docker is available
