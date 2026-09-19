# Persistent macOS gym-server service

Argentum's raw `gym-server` is intentionally loopback-first. On macOS it can run as a per-user `launchd` service so it survives terminal and chat sessions without exposing the unauthenticated API to the network.

## Install or refresh the service

From a clean Argentum checkout:

```bash
python3 scripts/gym_server_launchd.py install
```

The installer:

- resolves and records the exact current Git commit;
- refuses to install from a checkout with modified tracked files;
- resolves Java 21 from `JAVA_HOME` or `/usr/libexec/java_home -v 21`;
- installs `~/Library/LaunchAgents/io.argentum.gym-server.plist`;
- starts `gym-server` through `launchd` with `ARGENTUM_BUILD_REVISION` set to the recorded commit;
- forces `SERVER_ADDRESS=127.0.0.1`;
- enables `RunAtLoad` and `KeepAlive` restart behavior;
- writes stdout/stderr to deterministic paths under `~/Library/Logs/Argentum/gym-server/`;
- writes service metadata to `~/Library/Application Support/Argentum/gym-server/service.json`.

The service runner fails closed if the checkout later moves to a different commit or tracked files become dirty. After updating the Argentum checkout, rerun the install command to deliberately pin and restart the new revision.

## Inspect status

```bash
python3 scripts/gym_server_launchd.py status
```

This reports both the `launchd` state and `GET http://127.0.0.1:8081/status`, including the running build revision and observation schema hash.

For direct HTTP inspection:

```bash
curl http://127.0.0.1:8081/health
curl http://127.0.0.1:8081/status
```

## Stop and remove the launch agent

```bash
python3 scripts/gym_server_launchd.py uninstall
```

Uninstalling leaves logs and `service.json` in place for provenance.

## Security boundary

This service is not an internet-facing authentication layer. It deliberately binds the raw Argentum gym API to loopback. Remote clients should reach it only through a separately authenticated HTTPS gateway or secure tunnel. Do not change the service to bind `0.0.0.0` merely to make it remotely reachable.
