# Status Tacos

HTTP endpoint monitoring for teams. Status Tacos calls your URLs, decides if they are up, and
alerts you by email, webhook, Microsoft Teams or iPhone push when one goes down. Hosted at
[tacos.oglimmer.com](https://tacos.oglimmer.com), or run your own.

## Features

**Checks**

- Check interval from 15 seconds to 1 hour, connect timeout and alerting threshold per monitor.
- Up rules: status code regex, response body regex, or a Prometheus metric between a minimum and
  a maximum. Own request headers, for example a token.
- Each check asks the nameservers of the domain for the IP address, so a moved domain shows at
  the next check. Per monitor you can use the DNS server of the backend instead.
- States: active, silent (checks, no alerts) and inactive.
- Change the settings of many monitors at once, with progress per monitor.

**Alerts**

- Email, webhook, Microsoft Teams and iOS push. The all-clear goes out when the URL answers again.
- Per tenant: for all monitors or for selected monitors.
- Two servers can work as peers: before a DOWN alert, the other server checks the URL once. They
  also watch each other and report when one cannot send alerts.

**See it**

- Dashboard with uptime, response times and outages for 24 hours, 7 days and 90 days. The uptime
  counts time, so a changed check interval keeps it correct.
- Public status page per tenant, branded with your CSS, an own template, and fonts and images
  served from your own host.
- Grafana: Status Tacos is a Prometheus data source, signed in with an API token.
- iPhone and iPad app, and a Mac menu bar app with a Notification Center widget.

**Teams and access**

- Tenants, for example one per customer or per team, with members added by email.
- Sign-in with OIDC (Keycloak). Optional allow-list of email + login provider.
- Optional maximum number of monitors per user.

## Tech stack

| Part | Tech | Folder |
| --- | --- | --- |
| Frontend | Vue 3, TypeScript, Vite, Pinia | `frontend/` |
| Backend | Java, Spring Boot | `backend/` |
| Database | MariaDB | — |
| Auth | OIDC (Keycloak) | — |
| iOS app | SwiftUI | `ios-app/` |
| macOS app | SwiftUI, WidgetKit | `notifier-app/` |
| Deployment | Docker Compose, Helm | `compose.yml`, `helm/` |

## Quick start

```bash
# Run with Docker Compose
docker compose up

# Or use the helper script
./oglimmer.sh build
```

Open http://localhost:5173

## Development

### Frontend

```bash
cd frontend
npm install
npm run dev
npm run test:unit
```

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

## Deployment

- **Docker Compose:** `compose.yml`
- **Kubernetes:** Helm chart in `helm/`. Settings: [helm/README.md](helm/README.md).

## Docs

| Topic | Doc |
| --- | --- |
| Monitors: up rules, check settings, DNS, multi edit, monitor limit | [docs/monitors.md](docs/monitors.md) |
| Public status pages and branding | [docs/status-pages.md](docs/status-pages.md) |
| Grafana data source and API tokens | [docs/grafana.md](docs/grafana.md) |
| Two servers that work together (peer) | [docs/peer.md](docs/peer.md) |
| iOS push alerts | [docs/ios-push-alerts.md](docs/ios-push-alerts.md) |
| iOS app | [ios-app/README.md](ios-app/README.md) |
| macOS menu bar app | [notifier-app/README.md](notifier-app/README.md) |
| Helm chart and production | [helm/README.md](helm/README.md) |
