# Monitors

A monitor calls one URL at a fixed interval and decides if the answer is up or down. Each
monitor belongs to one tenant. Every member of the tenant sees it and can change it.

## Success criteria

A check is up when every rule you set passes. Without a status code regex, the status must be
2xx or 3xx.

| Rule | Up when | Example |
| --- | --- | --- |
| Status code regex | The HTTP status matches the regex | `^[23]\d{2}$` (default: any 2xx or 3xx) |
| Response body regex | The body contains a match | `"db":"ok"` |
| Prometheus metric | On a `/metrics` page, the sum of all lines that match the key (a regex) is between a minimum and a maximum | `queue_depth`, max `100` |

Your URL needs a token? Add request headers to the monitor. Every member of the tenant can see
them, so use a token that can only do what the check needs.

## Check settings

| Setting | Values | Default | What it does |
| --- | --- | --- | --- |
| Check interval | 15 to 3600 s, a multiple of 15 | 15 s | Time between two checks |
| Connect timeout | 1 to 10 s | 5 s | Maximum wait for the connection to the server |
| Alerting threshold | 15 s or more, a multiple of 15 | 30 s | Time down before the alert goes out |

The checks run at the multiples of the interval in wall-clock time, so all backend replicas agree
on the slot of a check. Monitors with a longer interval are spread over the slots by their id.

The uptime counts the time, not the checks: each check stands for its check interval. When you
change the interval, the uptime of the past stays correct.

## State

| State | Checks | Alerts |
| --- | --- | --- |
| Active | yes | yes |
| Silent | yes | no |
| Inactive | no | no |

Use Silent for planned maintenance.

## DNS

By default, each check asks the nameservers of the domain for the IP address. It starts at the
nameservers of the top-level domain and follows the referrals. So when a domain moves to a new
server or a new DNS provider, the next check already goes to the new server. A normal DNS server
can keep the old address for hours, and the check then measures the old server.

Hosts without a public zone (`localhost`, IP addresses, internal names) always use the DNS server
of the backend.

- **Per monitor:** tick "Use the DNS server of the backend" for names that only the DNS of the
  backend knows.
- **For all monitors:** set `monitor.http.dns.authoritative=false` (environment variable
  `MONITOR_HTTP_DNS_AUTHORITATIVE=false`).

When a check gets no HTTP status (timeout, DNS error, connection refused), the card shows the
error after "Status Code:".

## Change many monitors at once

1. On the dashboard, click **Select**.
2. Click the cards, or use **Select all N shown**. The filters decide what "shown" means.
3. Click **Edit N monitors**.
4. Tick the settings to change. Each setting shows the values the selected monitors have now,
   for example `Mixed: 15s (12) · 60s (3)`. Settings you do not tick stay as they are.

You can change: state, check interval, connect timeout, alerting threshold, status code regex,
"Use the DNS server of the backend" and "Show on the public status page". Name, URL, headers,
body regex and Prometheus are different for each endpoint, so they are not in the list. To move
a monitor to another tenant, edit the monitor.

The dialog shows the progress of each monitor. It sends 4 updates at the same time. **Stop**
stops the updates that did not start yet. At the end, **Retry** runs the failed ones again.

## Public status page

Tick "Show on the public status page" to show the monitor on the page of its tenant. See
[status-pages.md](status-pages.md).

## Monitor limit

`monitor.limits.max-monitors-per-user` (Helm: `backend.limits.maxMonitorsPerUser`) sets the
maximum number of monitors per user, in all their tenants. `0` (default) means no limit. The
column `users.max_monitors` overrides it for one user (`0`: no limit). Monitors of the peer
tenant do not count (see [peer.md](peer.md)).

The hosted instance at tacos.oglimmer.com allows 3 monitors per user. At the limit, the dashboard
shows a hint to run your own backend.
