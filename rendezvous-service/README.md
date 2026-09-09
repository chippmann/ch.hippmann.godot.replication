# Rendezvous service

The small service that makes internet play possible: it hands out session codes, tells a socket which public
address and port the internet sees it on, passes "knocks" between members so they can punch through their NATs,
and relays datagrams between two of its ports when punching fails. One JVM, no database, nothing persists.

## Configuration

Everything comes from the environment:

| Variable | Default | Meaning |
|---|---|---|
| `RENDEZVOUS_BIND_ADDRESS` | `0.0.0.0` | Address the HTTP, probe and relay sockets bind to |
| `RENDEZVOUS_HTTP_PORT` | `7789` | The JSON API the library talks to |
| `RENDEZVOUS_UDP_PORT` | `7790` | Where sockets send their probes to learn their public endpoint |
| `RENDEZVOUS_RELAY_PORT_RANGE` | `7800-7899` | Two ports per relayed link |
| `RENDEZVOUS_PUBLIC_ADDRESS` | reached host | Address the clients send UDP to; set it when the service sits behind a reverse proxy |
| `RENDEZVOUS_SESSION_TTL_SECONDS` | `45` | A code disappears this long after the host's last heartbeat |
| `RENDEZVOUS_RELAY_IDLE_SECONDS` | `120` | A relay pair is released after this much silence |

Run it locally with `./gradlew :rendezvous-service:run`, or build `./gradlew :rendezvous-service:installDist` and start
`build/install/rendezvous-service/bin/rendezvous-service`.

## Container image

CI publishes `ghcr.io/<owner>/godot-replication-rendezvous` (tags `sha-<commit>`, the branch name and `latest` on the
default branch) after the tests passed. The relay hands out UDP ports from a range, so run the container on the host
network instead of publishing a hundred ports through the Docker proxy:

```yaml
services:
  rendezvous:
    image: ghcr.io/<owner>/godot-replication-rendezvous:latest
    network_mode: host
    restart: unless-stopped
    environment:
      RENDEZVOUS_PUBLIC_ADDRESS: rendezvous.example.org
```

Open TCP 7789 and UDP 7790 plus 7800 to 7899 on the firewall and forward them from the router. Put the HTTP port behind
a TLS terminating reverse proxy if you like; the library's `rendezvousUrl` accepts `https://` and UDP still goes to
`RENDEZVOUS_PUBLIC_ADDRESS`.

## Automatic roll out from a private repository

The image workflow can notify a second repository through a `repository_dispatch` event, so that the repository which
holds the compose file (and its secrets) redeploys with its own runner. Nothing about that environment lives here.
In this repository set the variable `DEPLOY_REPOSITORY` to `<owner>/<deployment repository>` and the secret
`DEPLOY_DISPATCH_TOKEN` to a fine grained token that may write contents of that repository. In the deployment
repository, a workflow like this picks the event up:

```yaml
name: Deploy rendezvous

on:
  repository_dispatch:
    types: [rendezvous-image]

jobs:
  deploy:
    runs-on: [self-hosted, <label of the runner on the compose host>]
    steps:
      - uses: actions/checkout@v4
      - name: Roll out ${{ github.event.client_payload.image }}
        run: |
          echo "RENDEZVOUS_IMAGE=${{ github.event.client_payload.image }}" > rendezvous.env
          docker compose --env-file rendezvous.env pull rendezvous
          docker compose --env-file rendezvous.env up -d rendezvous
```

with `image: ${RENDEZVOUS_IMAGE}` in the compose file. The event carries the exact `sha-` tag, so a roll out is always
one specific build and rolling back means dispatching an older tag by hand.
