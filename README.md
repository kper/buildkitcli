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

## Publish the library

`lib` is configured with `maven-publish` and can publish to any Maven-compatible repository.

- If `MAVEN_REPOSITORY_URL` is set, `:lib:publish` pushes there.
- If `MAVEN_REPOSITORY_URL` is not set and the build runs in GitHub Actions, it falls back to the repository's GitHub Packages Maven URL.
- If `MAVEN_SIGNING_KEY` is present, the publication is signed with in-memory PGP keys.

Useful environment variables:

- `MAVEN_REPOSITORY_URL`
- `MAVEN_USERNAME`
- `MAVEN_PASSWORD`
- `MAVEN_SIGNING_KEY`
- `MAVEN_SIGNING_PASSWORD`

Local example:

```bash
./gradlew :lib:publish
```

## GitHub Actions

Two workflows are included:

- `test.yml`: builds and tests every module on pushes and pull requests
- `publish-lib.yml`: publishes only `:lib` on `v*` tags or manual dispatch

`publish-lib.yml` supports these secrets:

- `MAVEN_REPOSITORY_URL`: optional override for a non-GitHub Maven repository
- `MAVEN_USERNAME`: optional username override
- `MAVEN_PASSWORD`: optional password/token override
- `MAVEN_SIGNING_KEY`: optional ASCII-armored PGP private key
- `MAVEN_SIGNING_PASSWORD`: optional PGP key password

If you only want GitHub Packages, the default `GITHUB_TOKEN` plus `packages: write` permission is enough.

## Tests

`./gradlew build` runs:

- request-mapping unit tests
- session-bridge unit tests
- `FileSync` protocol tests
- container-backed integration tests when Docker is available
