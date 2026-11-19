# Where a game server can run

Type: research
Status: resolved

## Question

Which cheap hosting platforms can run a long-lived headless Java 25 process (a container) that peers on
the internet reach, and what does each allow? For **Render**, **Fly.io**, **Railway**, **Koyeb**, a small
VPS (Hetzner, OVH) and Oracle Cloud's free tier, find out:

- whether the public internet can reach it on **UDP**, on raw **TCP**, or only on **HTTP/WebSocket**
  through the platform's proxy;
- whether it sleeps when idle, and how long a cold start takes;
- the regions it offers, and whether one instance can serve players in Europe;
- limits on idle timeouts, on how long a connection may stay open, and on concurrent connections;
- the price for one small instance carrying about 8 rooms of up to 8 peers, at the few tens of kB/s each
  that ADR 0012 cites (`net.pacing.*` in `assets/network.properties`: about 10 kB/s per machine);
- how a deploy ships (Dockerfile, a JAR), and whether a fixed address or a stable hostname is available.

Primary sources only: the platforms' own docs and pricing pages, dated. The answer is a table plus which
platforms are viable for UDP and which force a WebSocket transport.

Findings: `research/01-where-a-game-server-can-run.md`.

## Answer

Read 2026-11-19 from each platform's own docs and pricing pages. Load: 64 peers x ~10 kB/s = ~640 kB/s
(~5 Mbit/s) out, ~2.3 GB/hour full, ~140 GB/month at 2 h/day, ~1.7 TB/month around the clock.

| Platform | Reachable on | Sleeps | Europe | ~Monthly (2 h/day / 24-7) | Address |
|---|---|---|---|---|---|
| Render | HTTP + WebSocket only, one port; WS dropped on every deploy and instance swap | Free: 15 min, ~1 min wake | Frankfurt | ~$27 / ~$260 ($0.15/GB) | `*.onrender.com` |
| Fly.io | UDP (dedicated IPv4, $2), raw TCP, HTTP/WS | Only if autostop on; ~2 s | ams, arn, cdg, fra, lhr | ~$9 / ~$40 ($0.02/GB) | `<app>.fly.dev`, static IPv4 |
| Railway | HTTP/WS, raw TCP via proxy; no inbound UDP | Only if Serverless on | Amsterdam | ~$17 / ~$95 ($0.05/GB) | `*.proxy.rlwy.net:<port>` |
| Koyeb | HTTP/WS (12 h max), raw TCP via proxy (preview); no UDP | Free: after 1 h | Frankfurt, Paris | ~$29 / ~$43 (Pro, 1 TB incl.) | proxy host + persistent random port |
| Hetzner VPS | anything (UDP, TCP, WS) | never | DE, FI | ~EUR 6 flat (20 TB incl.) | static IPv4 |
| OVH VPS | anything | never | FR, PL, DE, UK | ~$5 flat (unlimited, 500 Mbit/s) | static IPv4 |
| Oracle Always Free | anything | never, but an idle-looking VM may be reclaimed | home region (13 EU) | $0 (10 TB out; capacity not guaranteed) | public IP |

- **Viable for UDP:** Fly.io (dedicated IPv4, bind `fly-global-services`), Hetzner, OVH, Oracle.
- **Raw TCP but no UDP:** Railway, Koyeb.
- **Force WebSocket:** Render.
- Every platform ships a Dockerfile; the VMs also take a bare JAR. At this traffic, egress dominates compute:
  a EUR 5 VPS carries the full load flat; Fly is the only managed host with UDP and the cheapest per GB.

Findings and sources: [../research/01-where-a-game-server-can-run.md](../research/01-where-a-game-server-can-run.md)

### Addendum 2026-11-19: mikr.us

Asked after the ticket closed. Sources: mikr.us, wiki.mikr.us/udostepnione_porty,
wiki.mikr.us/o_co_chodzi_z_ipv6, wiki.mikr.us/ograniczenia_techniczne_mikrusa, all read 2026-11-19.

- **Technically it fits.**
  - Two public ports on a shared IPv4, 20000+ID and 30000+ID, take **TCP and UDP**. Our host listens on one
    port for both (`net.port`), so one of them is enough. Extra ports from the panel are TCP only, and no
    dedicated IPv4 is sold.
  - Each machine also has its own IPv6 address.
  - The machines are LXC containers at Hetzner in Helsinki. Plan 2.1 is 1 GB RAM for 75 zł a year; 3.0 is
    2 GB for 130 zł.
  - No transfer cap is stated: the 1 Gbps link is shared per physical server. CPU is throttled under
    sustained load, which a sequencer waking 60 times a second should not hit (unmeasured).
- **The rules exclude it as a public game server.** The main page forbids hosting game servers. The one
  exception is a developer testing his own game, which covers alpha playtests with friends and not a
  release. Confirm with mikr.us before relying on it.
