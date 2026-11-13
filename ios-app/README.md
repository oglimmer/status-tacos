# Status Tacos for iOS

A native iPhone and iPad client for Status Tacos. It shows your monitors and their most important data, and it can silence or disable a monitor. To add or edit monitors, use the web app.

## What it shows

**Monitor list**

- Count of monitors that are down, up and paused.
- Monitors grouped by health. Down monitors come first.
- Sort button: status (the groups above), name, last downtime, tenant then name, or created. Ascending or descending. The app keeps your choice.
- For each monitor: last response time (or the HTTP status when down), 7-day uptime, and a 7-day downtime bar.
- For down monitors: when the monitor was last up and the number of failed checks.
- Tenant selector at the top of the page. The counts and the list follow it.
- Search by name or URL.
- Swipe a row to silence, turn alerts on, pause or resume. Long-press a row for all states.
- Bell button: the push alert settings.
- Refreshes every 30 seconds and on pull-to-refresh.

**Monitor detail**

- Header: status as a colored word ("Up", "Down", "Paused", "No data") with the environment badge, below it how long it is up ("Up for 4 days") or why and how long it is down ("HTTP 503 · down 12 min"). Then URL (host), endpoint (path) and tenant in two columns; tap them to open the URL in the browser.
- Monitoring state: pick active (checks and alerts), silent (checks, no alerts) or paused (no checks). Each state has a color dot: green, orange, grey.
- Push on this iPhone: "Notify me" for this monitor (greyed when push is on for the whole tenant).
- Current status: last check, response time, status code, failed checks in a row, and last down (when up) or last up (when down). Status code and failed checks are red while the monitor is down.
- For 24 hours, 7 days or 90 days: uptime with the downtime of the window, average/P99/min/max response time (for 24 hours only from servers that send them), response time chart with downtime as red bands (short outages are widened so they stay visible), and the list of outages with the running one marked "Ongoing".
- Refreshes every 60 seconds.

**Push alerts**

- Native iOS notifications when a monitor goes down and when it comes back up. They work like the other alert contacts (email, webhook, Teams): same rules, same alert threshold, nothing for silent or inactive monitors.
- One push alert per tenant: for all monitors of the tenant (also new ones) or for selected monitors.
- They go to every device where you use the app with your account. Sign-out removes the device.
- Down alerts are "time sensitive": they show also in a Focus mode.
- Tap a notification to open the monitor.
- The web app shows push alerts read-only, with their owner. Only the owner changes them, in the app.

## Requirements

- Xcode 26 or later
- iOS 18 or later

## Sign-in setup (Keycloak)

The app signs in with OpenID Connect (authorization code flow with PKCE). It needs its own **public** client in the `status-tacos` realm:

| Setting | Value |
| --- | --- |
| Client ID | `status-tacos-ios` |
| Client authentication | Off (public client) |
| Standard flow | On |
| Valid redirect URIs | `de.oglimmer.statustacos:/oauth/callback` |
| PKCE method (Advanced) | `S256` |

The backend accepts the tokens of this client as-is: it checks only the signature of the token (`jwk-set-uri`).

You can change the server, issuer and client ID in the app under **Settings**. The redirect URI is fixed.

## Push notification setup

1. In Xcode, select the `StatusTacos` target → *Signing & Capabilities* → pick your team. Automatic signing adds the capabilities from `StatusTacos.entitlements`: **Push Notifications** and **Time Sensitive Notifications**.
2. On the server, set up APNs with a `.p8` key (see `helm/README.md`, "iOS Push Alerts").
3. In the app, tap the bell, allow notifications, and turn on push alerts for a tenant. Tap **Send Test Notification**.

Debug builds register a sandbox token, release builds a production token. The simulator gets real sandbox tokens on Apple silicon Macs, when the app is signed with your team.

To try the notification handling without a server:

```sh
xcrun simctl push booted de.oglimmer.statustacos payload.json
```

with a payload like the backend sends:

```json
{"aps": {"alert": {"title": "Down: Shop", "body": "HTTP 503 · Prod"}, "sound": "default",
         "interruption-level": "time-sensitive", "thread-id": "monitor-7"},
 "monitorId": 7, "tenantId": 1, "kind": "down"}
```

## Build and run

```sh
open ios-app/StatusTacos.xcodeproj
```

Select the `StatusTacos` scheme and a simulator, then press Run. To run on a device, select your team under *Signing & Capabilities*.

From the command line:

```sh
cd ios-app
xcodebuild -project StatusTacos.xcodeproj -scheme StatusTacos \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' test
```

### Demo mode

For screenshots without sign-in and server. Debug builds only (`App/DemoMode.swift`): the real screens and stores run against canned data (3 tenants, 23 monitors). No network, no Keychain, no push registration, no permission prompt.

| Launch argument | Effect |
| --- | --- |
| `-demo allUp` | All monitors up. |
| `-demo outage` | "Kammerportal Login" and "Meldebogen" (id 7) are down for 12 minutes, "Mail Gateway" is paused. |
| `-demoDetail <id>` | Opens the detail of that monitor (Meldebogen is 7). |
| `-demoScroll bottom` | With `-demoDetail`: scrolls the detail to its end. |
| `-monitorDetail.timeframe 7d` | Detail opens on 7d (or `90d`). Works for every `@AppStorage` key. |

Appearance comes from the simulator: `xcrun simctl ui <udid> appearance dark`.

```sh
cd ios-app
UDID=$(xcrun simctl list devices available | grep -F '    iPhone 17 (' | head -n 1 | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/')
xcrun simctl boot "$UDID"
xcodebuild -project StatusTacos.xcodeproj -scheme StatusTacos -configuration Debug \
  -destination "platform=iOS Simulator,id=$UDID" -derivedDataPath build/demo build
xcrun simctl install "$UDID" build/demo/Build/Products/Debug-iphonesimulator/StatusTacos.app
xcrun simctl launch "$UDID" de.oglimmer.statustacos -demo outage -demoDetail 7
xcrun simctl io "$UDID" screenshot detail.png
```

Launch again with `xcrun simctl terminate "$UDID" de.oglimmer.statustacos` first. The data starts at the time of each request, so "down 12 min" stays 12 minutes.

### Local backend

Start the backend (`docker compose up` in the repository root). In the app, open **Settings** and set the server to `http://localhost:8080`. The simulator reaches the Mac at `localhost`.

## Code layout

| Folder | Content |
| --- | --- |
| `App/` | App entry, root view, login view, settings storage |
| `Auth/` | OIDC client (PKCE, token refresh), Keychain token storage, sign-in state |
| `API/` | REST client and the wire types of the backend |
| `Monitors/` | Monitor list: data, store, views |
| `MonitorDetail/` | Monitor detail: data, store, views, response time chart |
| `Push/` | Push alerts: permission, device token, settings screen, notification taps |
| `Shared/` | Formatting and the downtime bar |
| `../StatusTacosTests/` | Unit tests (Swift Testing) |

The project uses folder references: Xcode picks up new files in these folders. You do not have to edit the project file.

Some rules are shared with the web app and must stay the same:

- Uptime is rounded down to 2 decimals (`Format.uptime`, `frontend/src/utils/uptime.ts`).
- Merging and clipping of down periods (`DowntimeTimeline.merged`). The bar itself has its own rule on iOS: grey track, one mark per outage at least 3 pt wide, red from 60 s on, yellow below (`DowntimeTimeline.marks`).
- Backend timestamps are UTC without a zone suffix (`BackendDate`).
