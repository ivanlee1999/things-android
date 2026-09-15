# Putting the phone on the backend

The app talks to the `things-cloud` service that already runs on the house server. Nothing here
changes that service; the work is making it reachable from a phone that is not on the LAN, and
keeping it shut to everyone else.

## What the app asks for, and why it is not your Things password

Two fields: **the server address** and **the API key**. That is the whole login.

There is no box for a Things account password, and adding one would not do anything. The backend
signs in to Things Cloud itself, with the credentials in `~/docker/things-cloud/.env.things`, and
serves a REST API over the mirror it keeps. It has no accounts of its own, no login endpoint and
no sessions — `/api/verify` checks *the server's* credentials, not anything a caller sends. So
"log in as me" has nothing to talk to.

That separation is worth keeping rather than working around:

- The API key can be rotated after a lost phone by editing `.env.things` and restarting the
  container. A Things password could not be, without changing it everywhere it is used.
- The key cannot be used to sign in to Things Cloud, or to anything else you own.
- The phone never holds the account credentials at all.

(The app could instead speak the Things Cloud protocol directly, the way the Mac and iPhone apps
do, and then it really would want the account password. That means porting a reverse-engineered
protocol and its sync engine into Kotlin and maintaining it alongside the Go one — a second
project, not a setting.)

## What actually needs guarding

The service exposes two things, and only one of them is behind the key:

| | |
|---|---|
| `/api/*` | guarded by `API_KEY` as a bearer token |
| `/mcp` | **no authentication at all** — the Model Context Protocol endpoint, deliberately open so a local Claude connector can use it |

So the only real hazard in publishing this hostname is `/mcp`. Anyone who found the address would
have full read and write on the account with no credential whatsoever. Everything below is about
closing that one door.

The same reasoning rules out pointing a tunnel at the `things-web` container on port 8098: that
nginx *injects* the API key on the way through and has no login of its own, so publishing it
would be publishing the account.

## The simple setup: block /mcp, then URL and key are enough

1. Add the backend's network to the tunnel's compose service, so `cloudflared` can reach it:

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

2. In the Cloudflare dashboard, add a public hostname on this tunnel:

   | | |
   |---|---|
   | Subdomain | `things` (or whatever you like) |
   | Service | `http://things-cloud:8080` |

3. Add one WAF custom rule on the zone (free plan includes five), so the open endpoint is never
   reachable from outside:

   ```
   (http.host eq "things.example.com" and starts_with(http.request.uri.path, "/mcp"))
   ```

   Action: **Block**. Your local Claude connector keeps using `/mcp` over the LAN or loopback,
   which this does not touch.

Then in the app: the server URL and the API key from `.env.things`. Leave both Cloudflare fields
blank. Tap **Test connection**.

What is left protecting the account is one long random bearer token over HTTPS, which is the
ordinary posture for an API token and is fine.

## If you want a second lock: Cloudflare Access

Optional, and the only reason the app has those two extra fields. Put an Access policy on the
hostname and create a **service token**; the app then sends `CF-Access-Client-Id` and
`CF-Access-Client-Secret` on every request, and anything without them never reaches the server.

It buys two things over the WAF rule: the hostname stops answering to scanners entirely, and a
leaked API key alone is not enough to use it. It costs a second credential to carry, and it is
the thing to blame first when the app says it cannot connect.

If you use it, fill in both fields; a bad token shows as "Cloudflare Access refused the request"
rather than as a network error.

## Using it at home only

The preview (`next`) APK is a debug build and accepts a cleartext address on a private network,
so `http://<server>:8097` works on the LAN without a tunnel — if the backend is published there.
By default it is loopback-only, which would need this in its compose file:

```yaml
    ports:
      - "192.168.0.x:8097:8080"   # the LAN address of this host
```

Think before doing that: anyone on the network then has the account, `/mcp` and all, with only
the key in the way and nothing in the way of `/mcp`. The signed release build refuses cleartext
entirely.
