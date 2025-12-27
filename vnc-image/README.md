# Kubernetes VNC Desktop Image

This folder contains the Dockerfile and startup scripts for the Linux desktop image used by the Guacamole Kubernetes extension.

## Features
- **Lightweight Desktop**: Uses XFCE4 for a responsive experience over VNC.
- **Pre-installed Apps**: Google Chrome (Stable), LibreOffice, and common terminal utilities.
- **Dynamic User Handling**: The entrypoint script automatically creates a local user matching the Guacamole user ID and sets up their home directory.
- **Persistence Support**: Designed to work with Kubernetes PVCs mounted at the user's home directory.

## Build Instructions

To build the image locally:

```bash
docker build -t guacamole-k8s-vnc:latest .
```

## Loading into `kind`

If you are using a `kind` cluster for development, load the image directly into your nodes:

```bash
kind load docker-image guacamole-k8s-vnc:latest --name <your-cluster-name>
```

## Customization

### Adding Software
Modify the `RUN apt-get install` block in the `Dockerfile` to add your desired packages.

### Entrypoint Logic (`entrypoint.sh`)
The entrypoint script does the following:
1. Creates a user and group based on environment variables (`VNC_USER`, `VNC_UID`, `VNC_GID`).
2. Fixes home directory permissions.
3. Sets the default working directory to the user's home folder.
4. Generates a TigerVNC `xstartup` script to launch XFCE.
5. Sets the VNC password.
6. Cleans up any stale X11 locks from previous pod runs.
7. Starts the VNC server and tails the logs.

## Requirements
- The image must have a VNC server listening on port `5901` (standard for display `:1`).
- The VNC password must be set via the `vncpasswd` utility during startup.
