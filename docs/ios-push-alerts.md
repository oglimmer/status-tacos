# iOS Push Alerts

Native iOS notifications as a new alert contact type, `IOS_PUSH`. They follow the same rules as the other alert contacts (email, HTTP, Teams). Only the delivery is new.

## Model

- **Push alert** = an `alert_contacts` row with `type = 'IOS_PUSH'` and `user_id` = its owner.
  - One per owner and tenant (`value = 'user:<id>'`, unique per tenant and type).
  - Scope like every contact: `all_monitors = 1`, or the monitors in `alert_contact_monitors`.
  - `is_active` pauses it.
- **Device** = a `push_devices` row: the APNs token of one app install, its environment (`SANDBOX` or `PRODUCTION`) and its owner.
  - A token is unique. When another user signs in on the same device, the token moves to that user.
- An alert goes to **all devices of the owner**.

Migration: `V0_0_17__add_ios_push_alerts.sql`. Deleting a user deletes their devices and push alerts.

## Who changes what

| | iOS app (owner) | Web app | Other users |
| --- | --- | --- | --- |
| See | own push alerts | all push alerts of the tenant, with owner | — |
| Create, change, pause, delete, test | yes (`/v1/push/...`) | no | no |

The generic `/v1/alert-contacts` endpoints refuse to change `IOS_PUSH` contacts.

## Delivery

`AlertService` sends push alerts with the other contacts, on the same DOWN and UP events. An `IOS_PUSH` contact is skipped when:

- APNs is off (`monitor.apns.enabled = false`),
- its owner is inactive, or no longer has access to the tenant of the monitor.

`ApnsClient` sends over HTTP/2 to APNs with a provider token. The token is an ES256 JWT, signed with the `.p8` key and renewed every 50 minutes.

| APNs answer | What happens |
| --- | --- |
| 200 | Delivered. The alert is written to the alert history (`IOS_PUSH: <owner email>`). |
| 410, `BadDeviceToken`, `Unregistered`, `DeviceTokenNotForTopic` | The device is deleted. |
| Anything else | Logged. The device stays. |

When no device gets the alert, nothing is written to the history. So the next check tries again, like for a failed email.

### The notification

| | Title | Body | Level |
| --- | --- | --- | --- |
| Down | `Down: <monitor>` | `HTTP 503 · <tenant>` (or the error) | time-sensitive |
| Up | `Up again: <monitor>` | `Was down for 12m · <tenant>` | active |
| Test | `Test notification` | `Push alerts work · <tenant>. <scope>.` | active |

- Down and Up of one monitor share `apns-collapse-id` and `thread-id` (`monitor-<id>`): the Up notification replaces the Down notification.
- The payload also carries `monitorId`, `tenantId` and `kind`. Tapping a notification opens the monitor in the app.
- APNs keeps trying for 1 hour (`apns-expiration`).

## REST API (iOS app)

| Method | Path | Body | Answer |
| --- | --- | --- | --- |
| GET | `/v1/push/status` | | `{enabled, devices}` |
| PUT | `/v1/push/devices` | `{token, environment, deviceName}` | 204 |
| DELETE | `/v1/push/devices/{token}` | | 204 |
| GET | `/v1/push/alerts` | | own push alerts |
| PUT | `/v1/push/alerts/{tenantId}` | `{isActive, allMonitors, monitorIds}` | the push alert |
| DELETE | `/v1/push/alerts/{tenantId}` | | 204, or 404 |
| POST | `/v1/push/alerts/{tenantId}/test` | | 200, 404, or 409 (no device got it) |

Errors: 400 and 409 come with `{"message": "..."}`. 403 for an unknown user or a tenant without access.

## Configuration

`monitor.apns.*` in `application.yml`, `backend.apns.*` in the Helm values. The key comes from the Secret `<release>-apns-secret`. See `helm/README.md`.

## Limits

- If the app signs out because the session expired (not by the user), it cannot remove its device. The device keeps getting alerts until the user signs in again (then the token moves to that user) or deletes the app (then APNs reports it gone).
- The app tells the backend the environment from its build type (Debug: sandbox). A Debug build that is signed for distribution would send the wrong environment.
