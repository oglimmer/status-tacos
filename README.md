# Status Tacos

A multi-tenant status monitoring application that tracks service availability and sends alerts.

## Tech Stack

- **Frontend**: Vue 3 + TypeScript + Vite
- **Backend**: Java Spring Boot
- **Database**: MariaDB
- **Auth**: OIDC

## Quick Start

```bash
# Run with Docker Compose
docker compose up

# Or use the helper script
./oglimmer.sh build
```

Access the app at http://localhost:5173

## Development

### Frontend
```bash
cd frontend
npm install
npm run dev
```

### Backend
```bash
cd backend
./mvnw spring-boot:run
```

## Deployment

- **Docker Compose**: `compose.yml`
- **Kubernetes**: Helm charts in `helm/`

## Features

- Monitor service uptime and availability
- Multi-tenant support
- Status charts and dashboards
- Alert notifications (Teams integration)
- OIDC authentication
