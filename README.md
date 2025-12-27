# Guacamole Kubernetes Dynamic VNC Extension

This extension for Apache Guacamole allows for the dynamic provisioning of isolated Linux desktop environments within a Kubernetes cluster. Every time a user initiates a connection, the extension ensures a dedicated Pod and Persistent Volume Claim (PVC) exist for that user, providing a personalized and persistent desktop experience.

## Features

- **🚀 On-Demand Provisioning**: Automatically creates Kubernetes Pods and PVCs when a user connects.
- **💾 Persistent Home Directories**: Each user gets a dedicated PVC mounted to `/home/<username>`, ensuring files persist across sessions.
- **🛡️ Multi-Cluster Support**: Configure different Kubernetes API endpoints per connection to deploy pods across multiple clusters.
- **⚙️ Dynamic Resource Allocation**: Define CPU, Memory limits, and PVC sizes directly in the Guacamole Connection UI.
- **🧩 Seamless Integration**: Works as a standard Guacamole protocol wrapper—no changes to the Guacamole core needed.

## Quick Start

1. **Build the extension**:
   ```bash
   mvn clean package
   ```
2. **Install**:
   Copy `target/guacamole-ext-k8s-vnc-1.0.0.jar` to your Guacamole extensions directory (`GUACAMOLE_HOME/extensions/`).
3. **Configure**:
   Add mandatory properties to `guacamole.properties` or environment variables (see [HOWTO.md](HOWTO.md)).
4. **Restart**:
   Restart your Guacamole container or application server.

## Documentation Index

- **[Configuration & Usage (HOWTO.md)](HOWTO.md)**: Detailed instructions on setting up environment variables, RBAC permissions, and using the UI connection parameters.
- **[Building the Desktop Image (vnc-image/README.md)](vnc-image/README.md)**: Instructions for building and customizing the XFCE-based VNC image used for the pods.

## How it Works

1. **Interception**: When a user selects a VNC connection, the extension intercepts the connection request.
2. **Validation**: It checks if Kubernetes provisioning is enabled for that specific connection.
3. **Provisioning**:
   - It ensures a PVC exists for the user/connection pair (and expands it if the requested size has increased).
   - It ensures a Pod is running with the specified image and resource limits.
4. **Routing**: Once the Pod is ready, the extension retrieves the internal Pod IP and transparently routes the VNC tunnel to it.
5. **Session Management**: Logs are tracked to ensure the connection is established only when the VNC server inside the Pod is fully initialized.
