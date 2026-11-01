# Two servers that work together (peer)

Two Status Tacos servers, A and B, can work together:

- **Ask a friend.** Before A sends a DOWN alert, B checks the monitor once.
  - B sees it DOWN too: A sends the alert.
  - B sees it UP: the problem is on A's side. A sends no alert. The card shows "Only this server sees DOWN".
  - B does not answer: A sends the alert, as without a peer.
- **Watch each other.** A has a normal monitor of B's heartbeat, and B one of A's. A server that does not answer is DOWN, with the normal alerts.
- **Cannot alert.** When A cannot send alerts, its heartbeat says `cannot-alert`. B's monitor of A then is DOWN and B alerts. A also tells B at once to check now. Only our own problems count:
  - Email or iOS push fails 3 times in a row.
  - Webhooks or Teams fail on 2 or more different hosts, with no successful send in between.
  - One successful send makes the channel healthy again.

Alerts are not relayed: B only says that A is in trouble.

## Setup

Do this on both servers. The example is for A.

1. Create a tenant for the servers, for example "Infra". Only admins should be members: they can see the token.
2. Set its id in the configuration: `monitor.peer.tenant-id` (Helm: `backend.peer.tenantId`). Restart.
3. On **B**, as a member of B's peer tenant: Account → API Tokens → type **Peer server** → Create. Copy the token.
4. On **A**, in A's peer tenant, add a monitor:
   - URL: `https://<B>/api/peer/heartbeat`
   - HTTP header: `Authorization: Bearer <token from step 3>`
   - Response body regex: `"status":"ok"`
   - Alerting threshold: how long B may be silent before you get an alert.
   - Alert contacts: the contacts of the peer tenant get the alerts about B.
5. Do steps 3 and 4 in the other direction.

The peer monitor has a **peer** badge. The dashboard shows a warning for members of the peer tenant when the setup is not valid: no peer monitor, more than one, or no `Authorization` header. Then the server works alone.

Monitors of the peer tenant do not count for the monitor limit of a user.

Tip: give both servers a different alert channel (for example not the same mail server). Else a broken mail server stops the alerts of both.

## Endpoints

All need a PEER token of a member of the peer tenant. A PEER token stops working when its user leaves the peer tenant. GRAFANA tokens do not work here, and PEER tokens do not work on `/prom`.

| Endpoint | What it does |
| --- | --- |
| `GET /api/peer/heartbeat` | `{"status":"ok"}` or `{"status":"cannot-alert","since":...,"reason":...}` |
| `POST /api/peer/check` | Checks a URL once with the settings of a monitor. Saves nothing. At most `monitor.peer.max-checks-per-minute` (120) a minute. |
| `POST /api/peer/distress` | "Check me now": checks the peer monitor at once (at most every 10 s). |

The check before an alert waits at most `monitor.peer.confirm-timeout` (12 s).

Note: `POST /api/peer/check` sends the monitor's HTTP headers to the other server. Use HTTPS for the peer URL.
