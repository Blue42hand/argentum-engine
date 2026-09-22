# Gym server request metrics

`gym-server` exposes Spring Boot Actuator's standard Micrometer HTTP request metrics for persistent-host qualification and performance diagnosis. This is transport observability only; it does not change the Gym environment contract or add player policy.

The server still binds to `127.0.0.1` by default. Remote deployments should keep an authenticated gateway, reverse proxy, or secure tunnel in front of the raw Gym server exactly as they do for the game/environment endpoints.

## Endpoints

- `GET /actuator/health` — standard Spring liveness/health view.
- `GET /actuator/metrics` — available meter names.
- `GET /actuator/metrics/http.server.requests` — completed HTTP request count and timing, with the normal Micrometer tags such as method, status, URI, and outcome.

Example after driving a fixed-seed qualification workload:

```bash
curl http://127.0.0.1:8081/actuator/metrics/http.server.requests
```

Filter one operation using Actuator's `tag` query parameter, for example:

```bash
curl 'http://127.0.0.1:8081/actuator/metrics/http.server.requests?tag=method:POST&tag=uri:/envs/{id}/step'
```

Use the returned `COUNT`, `TOTAL_TIME`, and `MAX` measurements together with the URI/method/status/outcome tags to distinguish server-side lifecycle cost and failures from trainer/model time. For a qualification run, preserve the exact Argentum build revision, seed/configuration, request counts, and the metric snapshot so performance evidence can be reproduced.

Actuator metrics are process-local and reset when the Gym server restarts. They intentionally do not contain downstream strategy, model data, deck-specific behavior, or deployment credentials.
