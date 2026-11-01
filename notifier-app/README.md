# Status Tacos Alert for macOS

A menu bar app for the Mac. It shows a macOS notification when a monitor goes down, and a
Notification Center widget shows which monitors are up or down.

## What it does

- **Menu bar icon:** a bell. A crossed-out bell means a connection problem; the popover tells
  you what is wrong.
- **Popover:** your account, the connection problem (if any), the settings, sign-out and quit.
- **Notifications:** "Monitor Down" with the monitor, the tenant and the down time, for each
  monitor that goes down. Click it to open the monitors page of the web app. The app asks the
  server (`GET /api/v1/alerts`) every 5 seconds.
- **Widget "Monitors"** (small, medium, large): an up/down sign and one tile per monitor. Down
  monitors come first, with their down time. The app shares the data with the widget through an
  App Group and reloads the widget only on a change.
- **Sign-in** stays valid: the app refreshes the access token before it expires and tries again
  once after a 401.

After you install a new build of the widget, run `killall chronod`. Otherwise macOS keeps the
old widget.

## Requirements

- Xcode 26 or later
- macOS 15.5 or later

## Sign-in setup (Keycloak)

The app signs in with OpenID Connect (authorization code flow with PKCE). It needs a **public**
client in the `status-tacos` realm:

| Setting | Value |
| --- | --- |
| Client ID | `notifier-app` |
| Client authentication | Off (public client) |
| Standard flow | On |
| Valid redirect URIs | `de.oglimmer.statustacos.alert:/oauth/callback` |
| PKCE method (Advanced) | `S256` |

You can change the server (default `https://tacos.oglimmer.com`), the issuer and the client ID
in the app settings. The redirect URI is fixed.

## Project

| Folder | Contents |
| --- | --- |
| `StatusTacosAlert/` | The menu bar app: sign-in, Keychain, polling, notifications, settings |
| `StatusTacosAlertWidget/` | The WidgetKit widget |
| `Shared/` | Code for both: the monitor snapshot and the date parsing of the backend |

Bundle IDs: `de.oglimmer.statustacos.alert` and `de.oglimmer.statustacos.alert.widget`.
