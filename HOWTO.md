# Configuration and Usage Guide

This guide covers the detailed configuration of the Kubernetes Dynamic VNC Extension.

## 1. Environment Variables / Properties

You can configure the extension using `guacamole.properties` or standard Environment Variables. Environment variables follow the naming convention: `K8S_VNC_PROPERTY_NAME` (uppercase with underscores).

### Global Configuration

| Property | Environment Variable | Default | Description |
|----------|----------------------|---------|-------------|
| `k8s-vnc-namespace` | `K8S_VNC_NAMESPACE` | `default` | The K8s namespace where pods/PVCs are created. |
| `k8s-vnc-password` | `K8S_VNC_PASSWORD` | **Required** | The VNC password to be set inside the pod. |
| `k8s-vnc-image` | `K8S_VNC_IMAGE` | `guacamole-k8s-vnc:latest` | The default container image to use. |
| `k8s-vnc-pvc-size` | `K8S_VNC_PVC_SIZE` | `1Gi` | Default disk size for user home directories. |
| `k8s-vnc-endpoint` | `K8S_VNC_ENDPOINT` | *Internal* | Kubernetes API URL (leave blank if running inside K8s). |
| `k8s-vnc-skip-tls-verify` | `K8S_VNC_SKIP_TLS_VERIFY` | `false` | Whether to ignore TLS certificate errors for the API. |

---

## 2. Guacamole UI Connection Parameters

In the Guacamole Admin UI, when editing a **VNC** connection, you will see a new section: **Kubernetes Pod Provisioning (VNC Only)**.

- **Enable Kubernetes Provisioning**: Must be set to **Enabled** to trigger the dynamic pod logic.
- **Kubernetes Namespace**: (Optional) Override the global namespace.
- **CPU limit**: (e.g., `1` or `500m`) Resource limits for the container.
- **Memory limit**: (e.g., `2Gi` or `1024Mi`) Memory limits for the container.
- **PVC size**: (e.g., `5Gi`) The requested size of the home directory volume. If increased later, the extension will attempt to expand the PVC.
- **Container Image**: Specify a specific image for this connection.
- **Kubernetes API Endpoint (URL)**: Target a specific cluster for this connection.
- **Skip TLS Verification**: Toggle for untrusted cluster endpoints.

---

## 3. Kubernetes RBAC Permissions

The service account running the Guacamole instance (or the user associated with the provided `kubeconfig`) must have the following permissions in the target namespace.

### Example Role

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: my-desktop-namespace
  name: guac-pod-manager
rules:
- apiGroups: [""]
  resources: ["pods", "persistentvolumeclaims"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: guac-pod-manager-binding
  namespace: my-desktop-namespace
subjects:
- kind: ServiceAccount
  name: guacamole-sa
  namespace: guacamole-system
roleRef:
  kind: Role
  name: guac-pod-manager
  apiGroup: rbac.authorization.k8s.io
```

---

## 4. Troubleshooting

### Logs
The extension logs directly to the Guacamole web application logs (usually found in Tomcat's `catalina.out` or via `kubectl logs` if Guacamole is in a pod). Look for prefix `K8sVNCConnection`.

### VNC Failures
- Ensure the **VNC Password** matches between the Guacamole configuration and what the Pod expects.
- Verify that the image specified has a working VNC server and is using the port specified in the connection (default `5901`).
- Check if the Pod is stuck in `Pending` (likely due to insufficient cluster resources or PVC binding issues).
