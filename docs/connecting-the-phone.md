# Putting the phone on the backend

The app talks to the `things-cloud` service that already runs on the house server. Nothing here
changes that service; the work is making it reachable from a phone that is not on the LAN, and
keeping it shut to everyone else.

## What is there today

```
things-cloud   127.0.0.1:8097 -> 8080   loopback only, guarded by one shared API_KEY
things-web     0.0.0.0:8098   -> 80     nginx, on the LAN, injects the key, no login of its own
```

Two things follow from that. The service speaks for a whole Things Cloud account through a
single bearer key, and its `/mcp` endpoint has **no authentication at all**. So the one thing
never to do is publish `:8098`, or route a tunnel at it: that is an unauthenticated door to the
account, and it would be open to the internet rather than to the living room.

## The tunnel

`cloudflared` already runs on this host, on its own compose network. It needs to reach
`things-cloud`, which is on another.

1. Add the backend's network to the tunnel's compose service:

   ```yaml
   # ~/docker/cloudflare-tunnel/docker-compose.yaml
   services:
     cloudflared:
       networks: [default, things]
   networks:
     things:
       name: things-cloud
       external: true
   ```

   Then `cd ~/docker/cloudflare-tunnel && docker compose up -d`.

2. In the Cloudflare dashboard, on this tunnel, add a public hostname:

   | | |
   |---|---|
   | Subdomain | `things` (or whatever you like) |
   | Service | `http://things-cloud:8080` |

   **Not** `http://things-web:80` and not port 8098. The API, not the web client.

3. Put a **Cloudflare Access** policy on that hostname, and create a **service token** for it.
   Access is what keeps the hostname private; the API key alone is a second lock on the same
   door, not a substitute, and `/mcp` sits behind neither of them.

## In the app

Settings → Connection:

| Field | Value |
|---|---|
| Server | `https://things.<your domain>` |
| API key | `API_KEY` from `~/docker/things-cloud/.env.things` |
| Cloudflare Access client ID | the service token's id, ending `.access` |
| Cloudflare Access client secret | the service token's secret |

Then **Test connection**. It fetches a real snapshot and tells you what it found, or which of the
four things was wrong — a refused key, a refused Access token, an unreachable host, or an address
this build will not send a key to.

## Using it at home only

The preview (`next`) APK is a debug build and accepts a cleartext address on a private network,
so `http://<server>:8097` works on the LAN without a tunnel — if the backend is published there.
By default it is loopback-only, which would need this in its compose file:

```yaml
    ports:
      - "192.168.0.x:8097:8080"   # the LAN address of this host
```

Think before doing that: anyone on the network then has the account, with only the key in the
way. The signed release build refuses cleartext entirely.
