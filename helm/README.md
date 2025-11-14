# Status Tacos Helm Chart

A Helm chart for deploying the Status Tacos application on Kubernetes.

## Overview

This chart deploys a full-stack Status Tacos application consisting of:
- **Frontend**: Vue-based web application
- **Backend**: Spring Boot API server
- **Database**: External MariaDB/MySQL database (not included)

## Prerequisites

- Kubernetes 1.19+
- Helm 3.2.0+
- External database (MariaDB/MySQL) accessible from the cluster
- Image pull secret for private registry access

## Installation

### Quick Start

```bash
# From the helm directory
helm install my-status-tacos ./helm
```

### Custom Values

```bash
# Install with custom values
helm install my-status-tacos ./helm -f custom-values.yaml
```

## Configuration

### Image Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `global.imageRegistry` | Global image registry | `registry.oglimmer.com` |
| `imagePullSecrets[0].name` | Image pull secret name | `oglimmerregistrykey` |
| `frontend.image.repository` | Frontend image repository | `status-tacos-frontend` |
| `frontend.image.tag` | Frontend image tag | `latest` |
| `backend.image.repository` | Backend image repository | `status-tacos-backend` |
| `backend.image.tag` | Backend image tag | `latest` |

### Frontend Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `frontend.replicaCount` | Number of frontend replicas | `2` |
| `frontend.service.type` | Frontend service type | `ClusterIP` |
| `frontend.service.port` | Frontend service port | `8080` |
| `frontend.resources.requests.cpu` | CPU request | `100m` |
| `frontend.resources.requests.memory` | Memory request | `128Mi` |

### Backend Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `backend.replicaCount` | Number of backend replicas | `2` |
| `backend.service.type` | Backend service type | `ClusterIP` |
| `backend.service.port` | Backend service port | `8080` |
| `backend.env.springProfilesActive` | Spring profiles | `prod` |
| `backend.resources.requests.cpu` | CPU request | `250m` |
| `backend.resources.requests.memory` | Memory request | `512Mi` |

### Database Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `database.host` | Database host | `mariadb` |
| `database.port` | Database port | `3306` |
| `database.name` | Database name | `status-tacos` |
| `database.username` | Database username | `status-tacos` |
| `database.password` | Database password | `foobar` |

### Ingress Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `ingress.enabled` | Enable ingress | `true` |
| `ingress.className` | Ingress class name | `""` |
| `ingress.hosts[0].host` | Hostname | `tacos.oglimmer.com` |
| `ingress.certManager.enabled` | Enable cert-manager annotation | `true` |
| `ingress.certManager.clusterIssuer` | Cert-manager cluster issuer | `oglimmer-com-dns` |
| `ingress.tls.enabled` | Enable TLS | `true` |
| `ingress.tls.secretName` | TLS secret name | `tls-status-tacos-ingress-dns` |
| `ingress.tls.hosts` | TLS hostnames | `["tacos.oglimmer.com"]` |

## Examples

### Production Deployment

```yaml
frontend:
  replicaCount: 1

backend:
  replicaCount: 1

database:
  password: "secure-password"
```


## Upgrading

```bash
# Upgrade to a new version
helm upgrade my-status-tacos ./helm

# Upgrade with new values
helm upgrade my-status-tacos ./helm -f new-values.yaml
```

## Uninstalling

```bash
# Uninstall the release
helm uninstall my-status-tacos
```

## Monitoring and Health Checks

The chart includes comprehensive health checks:

### Backend Health Checks
- **Liveness Probe**: `/api/actuator/health` (Spring Boot Actuator)
- **Readiness Probe**: `/api/actuator/health`

### Frontend Health Checks
- **Liveness/Readiness Probe**: HTTP GET on root path

## Security

- Runs with non-root security context
- Database credentials stored in Kubernetes secrets
- Configurable service accounts
- Pod security contexts enforced
- Private registry authentication via image pull secrets

## Using Sealed Secrets

Sealed Secrets let you store encrypted secrets in Git and have them decrypted in the cluster by the Sealed Secrets controller.

What this chart expects
- Secret name: `<release-name>-database-secret` (rendered from `{{ include "status-tacos.fullname" . }}-database-secret`)
- Secret key: `database-password`
- The backend `Deployment` reads `SPRING_DATASOURCE_PASSWORD` from that Secret key.

Prerequisites
- Install the Sealed Secrets controller in your cluster and the `kubeseal` CLI.
  - Docs: https://github.com/bitnami-labs/sealed-secrets

Steps
1) Create a plain Secret manifest locally (do not apply it):
   ```bash
   NAMESPACE=default
   RELEASE=my-status-tacos
   DB_PASSWORD='change-me'

   kubectl -n "$NAMESPACE" create secret generic "$RELEASE-database-secret" \
     --from-literal=database-password="$DB_PASSWORD" \
     --dry-run=client -o yaml > db-secret.yaml
   ```

2) Seal the Secret for your cluster/namespace:
   ```bash
   kubeseal -n "$NAMESPACE" --format yaml < db-secret.yaml > sealed-database-secret.yaml
   ```

3) Commit and apply the SealedSecret:
   ```bash
   kubectl apply -n "$NAMESPACE" -f sealed-database-secret.yaml
   ```

4) Install/upgrade the chart pointing at the same namespace and release name:
   ```bash
   helm upgrade --install "$RELEASE" ./helm -n "$NAMESPACE"
   ```

Important: avoid double-creating the Secret
- This chart currently renders `helm/templates/database-secret.yaml`, which also creates the database `Secret` from `values.yaml`.
- To rely solely on your `SealedSecret`, use one of the following approaches:
  - Maintain a small fork and remove `helm/templates/database-secret.yaml`.
  - Or use a Helm post-renderer to drop that one Secret from the rendered output. Example using `yq`:
    ```bash
    cat > drop-db-secret.sh <<'EOF'
    #!/usr/bin/env bash
    yq 'del(.[] | select(.kind == "Secret" and .metadata.name == env(RELEASE) + "-database-secret"))'
    EOF
    chmod +x drop-db-secret.sh

    RELEASE=my-status-tacos NAMESPACE=default \
    helm upgrade --install "$RELEASE" ./helm -n "$NAMESPACE" \
      --post-renderer ./drop-db-secret.sh
    ```

Notes
- Ensure the `SealedSecret` is created in the same namespace and with the exact name the chart expects.
- You can rotate the password by re-sealing a new value and reapplying the `SealedSecret`; the controller updates the underlying `Secret` and pods will pick it up on restart/rollout.

## Troubleshooting

### Common Issues

1. **Backend can't connect to database**
   ```bash
   # Check database connectivity
   kubectl logs deployment/my-status-tacos-backend
   ```

2. **Images not pulling**
   ```bash
   # Check image registry and credentials
   kubectl describe pod <pod-name>
   ```

3. **Ingress not working**
   ```bash
   # Check ingress controller and DNS
   kubectl get ingress
   kubectl describe ingress my-status-tacos
   ```

### Debugging Commands

```bash
# Check all resources
kubectl get all -l app.kubernetes.io/instance=my-status-tacos

# Check secrets
kubectl get secrets | grep status-tacos

# View logs
kubectl logs -l app.kubernetes.io/name=status-tacos

# Port forward for testing
kubectl port-forward svc/my-status-tacos-frontend 8080:8080
kubectl port-forward svc/my-status-tacos-backend 8081:8080
```
