# Things for Android

A native client for [Things 3](https://culturedcode.com/things/), drawn the way the Mac and
iPhone apps are, for a phone — and for a BOOX Palma, which is a phone with an e-ink screen.

It is the Android counterpart of [`things-web`](../things-web): the same lists, the same
grouping, the same palette and the same wording, over the same self-hosted `things-cloud`
service, which mirrors a Things Cloud account and speaks a small REST API.

## What it does

- **Every list Things has** — Inbox, Today, Upcoming, Anytime, Someday, Logbook, Trash, then
  areas with their projects, headings inside a project, and tags.
- **Works with no signal.** Everything is written to a local copy first and queued; the queue
  drains when the phone can reach the server. A to-do typed on a train is there when you look
  again, and on the server when the train arrives.
- **E-ink mode**, a switch in Settings: black on white, no animation, borders instead of
  shadows, bigger targets, and the volume keys turn pages. Off by default, because a BOOX is
  still an Android phone and which of the two looks you want on it is a preference.
- **Share to Inbox**, and app shortcuts for "New To-Do" and "Today" — which is also what the
  Palma's smart button can be pointed at.

## Getting it onto a phone

Builds happen on GitHub Actions, never locally.

- **Preview** — every push to `main` publishes `things-next.apk` to the `next` prerelease.
- **Release** — run the *Release* workflow by hand; it builds a signed APK and tags it.

Both are signed with the same key, so one updates over the other without an uninstall.

## Connecting it

See [docs/connecting-the-phone.md](docs/connecting-the-phone.md) for the tunnel and the Access
policy in front of it.


Settings → Connection wants two things: the address of your `things-cloud` server and its
`API_KEY`. There is no Things account password to enter — the backend holds the account and
speaks for it, and the key is the key to *that server*, which is what makes it rotatable after
a lost phone without touching your Things login.

The key stands in for the whole account, so a release build sends it over HTTPS only; a preview
(debug) build also accepts an `http://` address on a private network, for use at home.

One thing to get right before exposing the server: its `/mcp` endpoint has **no authentication
at all**, by design, so that a local Claude connector can use it. Block that path at Cloudflare,
or put Access in front of the hostname. The two Cloudflare fields in Settings are for the latter
and are otherwise left blank.

## Shape

```
model/        dates, the view classifier, the optimistic patcher — pure Kotlin, unit-tested
data/api      the REST client and its wire types
data/db       the Room mirror of one /api/snapshot
data/outbox   writes that have not been sent yet, and the id swap when they are
data/repo     the repository: patch locally, queue, drain, re-read
data/settings the server, the secrets, and what the app looks like
sync/         when to poll, and when not to redraw under a reader
ui/theme      the palette, the type scale, and every e-ink rule, each in one place
ui/           the screens, the rows, the card, the pickers
```

Three things are ported line for line from the web client and must stay in step with it and
with the backend's SQL, because all three answer the same question about which list a to-do is
in: `classify`, `applyWhen`, and the edit-field mapping where the string `"none"` clears a
field. The tests in `model/` are written as the table that says what they agree on.

## Licence

The app is MIT. It bundles [Inter](https://rsms.me/inter/) under the SIL Open Font License; see
`LICENSE-Inter.txt`.
