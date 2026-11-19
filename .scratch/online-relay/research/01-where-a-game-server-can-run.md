# Where a game server can run - findings

Ticket: `../issues/01-where-a-game-server-can-run.md`. Every source below was read on **2026-11-19**, from the
platform's own docs or pricing pages unless marked *(first-party, not docs)*. Prices exclude VAT.

## The load we are sizing for

Eight rooms of up to eight peers = 64 peers on one process. At the ~10 kB/s per machine the ticket cites
(ADR 0012, `net.pacing.*`), a star through the server sends about 64 x 10 kB/s = **640 kB/s (~5 Mbit/s) out**,
and roughly as much in. That is **~2.3 GB per hour** with every seat full, **~140 GB/month** at two full hours
a day, and **~1.7 TB/month** if full around the clock. CPU and memory are small: a relay or sequencer for
64 peers fits a fraction of a vCPU and a 256-512 MB JVM. **Egress price is what separates the platforms.**

## Summary table

| Platform | Public internet reaches it on | Sleeps when idle | Europe | Connection limits | Small instance | Egress | Rough monthly cost (2 h/day full / 24-7 full) | Deploy and address |
|---|---|---|---|---|---|---|---|---|
| **Render** | **HTTP/HTTPS + WebSocket only**, one port | Free: after 15 min, ~1 min to wake; paid: no | Frankfurt | WS: no max duration, no connection cap; closed on every deploy and periodic instance swap | Free 0.1 CPU/512 MB; Starter 0.5 CPU/512 MB $7 | Hobby 5 GB incl., then **$0.15/GB** | ~$27 / ~$260 | Dockerfile or image; `*.onrender.com` hostname |
| **Fly.io** | **UDP** (dedicated IPv4 only), **raw TCP**, HTTP/WS | Only if `auto_stop_machines` on; cold start ~2 s, resume ~100s ms | ams, arn, cdg, fra, lhr | Default `soft_limit` 20 connections (a scaling hint), no hard limit unless set | shared-cpu-1x 512 MB $3.69 (+ $2 dedicated IPv4) | **$0.02/GB** EU/NA | ~$9 / ~$40 | Dockerfile/image; `<app>.fly.dev`, static dedicated IPv4 |
| **Railway** | HTTP/WS, **raw TCP via TCP proxy** (`host:port`); **no inbound UDP** | Only if Serverless enabled: 5-10 min, first request may 502 | Amsterdam | WS open indefinitely; HTTP 15 min max | Usage-based: ~$10/GB-RAM-month, ~$20/vCPU-month; Hobby $5 incl. $5 | **$0.05/GB** | ~$17 / ~$95 | Dockerfile; generated `*.proxy.rlwy.net:<port>` |
| **Koyeb** | HTTP/HTTP2/WS, **raw TCP via TCP proxy** (public preview); **no UDP** | Free: scale to zero after 1 h; TCP proxy incompatible with scale-to-zero | Frankfurt, Paris | WS max 12 h with keep-alives; HTTP connection timeout 100 s | Free 0.1 vCPU/512 MB (FRA or WAS); micro 0.5/512 MB $5.36 | Pro $29 incl. 1 TB, then $0.02/GB | ~$29 (Pro) / ~$43 | Docker or buildpacks; proxy host + random but persistent port |
| **Hetzner Cloud** (VPS) | **Anything** - a VM with a public IPv4; firewall filters TCP, UDP, ICMP | Never | Falkenstein, Nuremberg (DE), Helsinki (FI) | None but the VM's | CPX02 1 vCPU/1 GB EUR 5.49 (HEL1) / 6.49; CX23 2 vCPU/4 GB EUR 5.99 | **20 TB included** (EU) | **~EUR 6 flat** either way | JAR or Docker, yours to run; static IPv4 included |
| **OVHcloud VPS** | **Anything** - a VM with a public IPv4 | Never | Gravelines, Strasbourg (FR), Warsaw (PL), Limburg (DE), Erith (UK) | None but the VM's | VPS-1 2 vCores/4 GB, from $4.54 (GBP 3.31) | **Unlimited**, 500 Mbit/s | **~$5 flat** either way | JAR or Docker; IPv4 included |
| **Oracle Cloud Always Free** | **Anything** - a VM with a public IP; security rules allow TCP, UDP, ICMP | Never sleeps, but **idle instances may be reclaimed** | Home region only; 13 EU regions to choose from at sign-up | E2.1.Micro: 50 Mbit/s internet | A1.Flex 2 OCPU/12 GB total, or 2x E2.1.Micro 1/8 OCPU/1 GB | **10 TB/month free** | **$0** (capacity not guaranteed) | JAR or Docker; one public IP per VNIC |

Cost column: instance price plus egress at 140 GB and ~1.7 TB a month; ingress is free or counted the same
way and is left out. Railway compute is an estimate from its per-second rates for ~0.5 GB and ~0.25 vCPU.

## Which platforms carry UDP, which force WebSocket

- **UDP from the internet: Fly.io (with a $2/month dedicated IPv4), Hetzner, OVH, Oracle.** On Fly the UDP
  socket must bind `fly-global-services`, the internal and external port must match, there is no public-IPv6
  UDP, and there is no per-flow load balancing (fine for one machine). The three VMs are plain hosts.
- **Raw TCP but no UDP: Railway (TCP proxy), Koyeb (TCP proxy, public preview).** A plain TCP stream works,
  through a platform-chosen `host:port`.
- **WebSocket only: Render.** A web service gets one HTTP port behind Render's load balancer; private services
  and background workers are not reachable from the internet at all. A Render server forces a WebSocket
  transport adapter behind `Transport`.

## Per-platform notes and sources

### Render

- Public traffic: "Your web service must bind to a port on host 0.0.0.0 to receive HTTP requests from the
  public internet"; Render "forwards inbound traffic to only one HTTP port per web service"; WebSocket and
  HTTP/2 listed as supported. No raw TCP or UDP mentioned. - https://render.com/docs/web-services
- Private services "aren't reachable via the public internet"; background workers "can't receive" inbound
  traffic. - https://render.com/docs/private-services
- WebSocket: "Render does not enforce a maximum duration for WebSocket connections"; no connection limit; they
  "close automatically when the instance is replaced (for example, during a deploy)", and Render "periodically
  swaps out your web service's running instances" with a 30-second graceful window. Recommends keepalives.
  **Relevant to the reconnect ticket: a Render server drops every room on each deploy and maintenance swap.**
  - https://render.com/docs/websocket
- Free: spins down after "15 minutes without receiving any inbound traffic" (HTTP or WebSocket messages
  count); waking "takes about one minute"; 750 free instance hours per month. - https://render.com/docs/free
- Plans: free 0.1 CPU/512 MB, `0.5c-512mb` (Starter) 0.5 CPU/512 MB. - https://render.com/docs/compute-plans.
  Starter at $7/month per the pricing page as surfaced by search (the page renders client-side and could not be
  read directly) - https://render.com/pricing
- Outbound bandwidth: Hobby 5 GB, Pro 25 GB, Scale 1 TB included; "$0.15 per GB" beyond.
  - https://render.com/docs/outbound-bandwidth
- Regions: Oregon, Ohio, Virginia, Frankfurt, Singapore. - https://render.com/docs/regions
- Deploy: "fully supports Docker-based deploys", from a Dockerfile or a registry image. - https://render.com/docs/docker

### Fly.io

- UDP: "You can't use a shared IPv4 address or an IPv6 address for UDP"; bind to `fly-global-services`
  ("`0.0.0.0` ... generally won't do"); "Fly will only rewrite the IP addresses for UDP packets" (ports must
  match); "Don't assume a particular MTU for UDP" (WireGuard adds 60 bytes, the proxy about a dozen more);
  "There's no per-flow load balancing for UDP". - https://docs.fly.io/networking/udp-and-tcp
- Raw TCP: "If you don't specify handlers, we just forward TCP to your app as-is." Unhandled raw TCP is listed
  among the reasons for a dedicated IPv4. - https://docs.fly.io/networking/services
- Concurrency: `type` defaults to `connections`; `soft_limit` "If unset, the default is 20"; "If unset, no
  `hard_limit` is enforced". `idle_timeout` exists under `http_options` only, no documented default.
  `[[services]] protocol` may be `tcp` or `udp`. - https://docs.fly.io/reference/configuration
- Sleep: only with `auto_stop_machines`; "A cold start takes about 2 seconds or more"; resume from suspend "a
  few hundred milliseconds"; set `auto_stop_machines = "off"` to keep it on. The page does not say autostart
  works for UDP, so a UDP server should run with autostop off. - https://docs.fly.io/launch/autostop-autostart
- Pricing: shared-cpu-1x 256 MB $2.19, 512 MB $3.69, 1 GB $6.70 a month; dedicated IPv4 $2/month; egress
  $0.02/GB in North America and Europe; inbound free; no free tier for new orgs (a $1, 7-day trial); bills under
  $5 waived for personal orgs. - https://docs.fly.io/about/pricing
- Europe: ams, arn, cdg, fra, lhr. - https://docs.fly.io/reference/regions

### Railway

- Public networking is HTTP/HTTPS; the TCP proxy "generates a domain and proxy port, and all traffic sent to
  `domain:port` will be proxied to the service"; the port "is provided by Railway and must be used"; the docs
  do not say whether it stays the same. - https://docs.railway.com/reference/public-networking,
  https://docs.railway.com/reference/tcp-proxy
- Limits: HTTP up to 15 minutes while data flows, 5 minutes idle; WebSockets "can stay open indefinitely, even
  while idle"; no concurrent-connection figure; UDP not mentioned.
  - https://docs.railway.com/networking/public-networking/specs-and-limits
- UDP: private networking carries UDP but is not reachable from the internet
  (https://docs.railway.com/private-networking). Inbound UDP is not supported, per a Railway staff reply
  (https://station.railway.com/questions/does-railway-allow-external-udp-traffic-e36d0dc8) and Railway's LiveKit
  template, which says Railway routes no inbound UDP (https://railway.com/deploy/livekit-server) *(first-party,
  not docs)*.
- Sleep: Serverless is per service; inactive "after 5 minutes" without outbound packets, sleeps "between 5 and
  10 minutes"; the first request to a slept service "may return a 502". - https://docs.railway.com/reference/app-sleeping
- Pricing: Free $0 with $1/month; Hobby $5 including $5 usage; Pro $20; vCPU ~$20/month, memory ~$10/GB-month,
  egress $0.05/GB. - https://railway.com/pricing
- Europe: EU West Metal, Amsterdam. - https://docs.railway.com/reference/regions

### Koyeb

- Exposure: public routes are `HTTP` or `HTTP/2` (https://www.koyeb.com/docs/build-and-deploy/exposing-your-service).
  TCP: "TCP Proxy is currently in public preview"; the proxy port is "randomly generated and not predictable",
  persists across deployments and pause/resume; "TCP Proxy is not compatible with Scale-to-Zero at the moment".
  UDP not mentioned. - https://www.koyeb.com/docs/run-and-scale/tcp-proxy
- Timeouts: HTTP connection timeout 100 seconds; WebSockets and gRPC streams up to 12 hours with client
  keep-alives. - https://www.koyeb.com/docs/reference/edge-network
- Instances: free 0.1 vCPU/512 MB (one per organization, Frankfurt or Washington, scales to zero after 1 hour
  without traffic); eco-nano $1.61; eco-micro $2.68; nano $2.68; micro 0.5/512 MB $5.36; small 1/1 GB $10.71.
  - https://www.koyeb.com/docs/reference/instances
- Plans: Pro $29 including $10 compute and 1 TB bandwidth, extra $0.02/GB (EU/US); Scale $299. Whether a paid
  instance needs Pro was not established. - https://www.koyeb.com/pricing
- Europe: Frankfurt, Paris. - https://www.koyeb.com/docs/reference/regions

### Hetzner Cloud

- Plans (EU, incl. IPv4, 20 TB traffic): CPX02 1 vCPU/1 GB EUR 5.49 (HEL1) / 6.49 (FSN1, NBG1); CX23 2 vCPU/4 GB
  EUR 5.99; CAX11 (Arm) 2/4 GB EUR 6.49. Extra traffic is charged per TB (EUR 1.00). Locations FSN1, NBG1
  (Germany), HEL1 (Finland). **On the day read, every plan's create button showed "currently unavailable"** - a
  stock shortage to check before relying on it. - https://www.hetzner.com/cloud/regular-performance/,
  https://www.hetzner.com/cloud/cost-optimized/
- Firewall: "You can filter TCP, UDP, ICMP, ESP and GRE traffic." - https://docs.hetzner.com/cloud/firewalls/faq/

### OVHcloud VPS

- VPS-1: 2 vCores, 4 GB, 40 GB NVMe, 500 Mbit/s, "Unlimited traffic", from $4.54 (GBP 3.31) a month; anti-DDoS
  included. - https://www.ovhcloud.com/en/vps/, https://www.ovhcloud.com/en-gb/vps/
- Every VPS comes with an IPv4 and an IPv6. - https://www.ovhcloud.com/en/vps/vps-ip/
- European regions: Gravelines, Strasbourg, Warsaw, Limburg, Erith. Which of them sell VPS-1 was not
  established. - https://www.ovhcloud.com/en/about-us/global-infrastructure/regions/

### Oracle Cloud Always Free

- A1.Flex: "2 OCPUs and 12 GB of memory" for Always Free tenancies. Two
  E2.1.Micro: 1/8 OCPU, 1 GB, "one public IP address and up to 50 Mbps network bandwidth via the internet".
  "10 TB per month of outbound data." Instances only in the home region. "Out of host capacity" errors are
  expected. Idle reclaim: over 7 days, 95th-percentile CPU < 20%, network < 20%, and (A1) memory < 20%.
  **A light game server on A1 is idle by that test** (CPU and network low; a small JVM heap is under 20% of
  12 GB), so it is at risk of being reclaimed. - https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm
- Security rules allow "a single IPv4 protocol ... or 'all'", TCP and UDP with ports; the default list opens
  only SSH and some ICMP. - https://docs.oracle.com/en-us/iaas/Content/Network/Concepts/securityrules.htm,
  https://docs.oracle.com/en-us/iaas/Content/Network/Concepts/securitylists.htm
- EU regions: Paris, Marseille, Frankfurt, Milan, Turin, Amsterdam, Jovanovac, Madrid (2), Stockholm, Zurich,
  London, Newport. - https://docs.oracle.com/en-us/iaas/Content/General/Concepts/regions.htm

## Not established

- Whether Fly autostarts a stopped machine on UDP traffic (undocumented; run with autostop off).
- Whether Railway's TCP proxy port survives redeploys; whether Koyeb's TCP proxy is open to free instances.
- Idle timeouts on Fly's and Railway's raw TCP proxies (none documented).
- Render's Starter price was read through search, not from the pricing page itself.

## What this means for the server

- Europe is covered everywhere; one instance in Frankfurt or Amsterdam serves the continent.
- At this traffic the per-GB platforms cost more in bandwidth than in compute. A EUR 5 VPS (Hetzner, OVH)
  carries the full load flat, with UDP, TCP and WebSocket all open, a static IP, and no sleep - at the price of
  running the box ourselves.
- Of the managed platforms only Fly carries UDP, and it is also the cheapest per GB.
- Render forces WebSocket and drops every connection on each deploy and instance swap - workable only with the
  reconnect work this map plans, and the dearest at volume.
- A transport that must run on every candidate is WebSocket over TCP; a transport over UDP rules out Render,
  Railway and Koyeb.
