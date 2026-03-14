# buildkit-java-client

Pure Java BuildKit client plus a separate CLI built on top of the library.

## Modules

- `lib`: publishable Java library with the BuildKit client API
- `cli`: command-line application that depends on `:lib`

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
./gradlew build
```

## Run the CLI

```bash
./gradlew :cli:run --args='--addr unix:///run/buildkit/buildkitd.sock --context /tmp/app --dockerfile /tmp/app/Dockerfile --image example.com/demo:latest'
```

```bash
./gradlew :cli:run --args='--addr tcp://localhost:1234 --context /tmp/app --dockerfile /tmp/app/Dockerfile --image example.com/demo:latest'
```

To load the result into your local Docker daemon instead of exporting it as a BuildKit image:

```bash
./gradlew :cli:run --args='--addr unix:///run/buildkit/buildkitd.sock --context /tmp/app --dockerfile /tmp/app/Dockerfile --image local/demo:latest --load'
```

## Use the library

```java
import io.github.kper.buildkitcli.lib.BuildProgressListener;
import io.github.kper.buildkitcli.lib.BuildResult;
import io.github.kper.buildkitcli.lib.BuildkitClient;
import io.github.kper.buildkitcli.lib.BuildkitConnectionConfig;
import io.github.kper.buildkitcli.lib.DockerfileBuildRequest;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

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

try (BuildkitClient client = new BuildkitClient(config)) {
    BuildResult result = client.buildImage(request, BuildProgressListener.NOOP);
    System.out.println(result.imageDigest());
}
```

## Tests

`./gradlew build`
