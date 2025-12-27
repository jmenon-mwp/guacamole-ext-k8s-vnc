#!/bin/bash
set -e

# Default configuration from Environment or defaults
USER_NAME=${VNC_USER:-guacuser}
USER_UID=${VNC_UID:-1000}
USER_GID=${VNC_GID:-1000}
RESOLUTION=${VNC_RESOLUTION:-1280x800}
DEPTH=${VNC_COL_DEPTH:-24}
PASSWORD=${VNC_PW:-guacamole}

echo "Starting VNC Container..."
echo "User: $USER_NAME ($USER_UID:$USER_GID)"

# 1. Create Group if it doesn't exist
if ! getent group "$USER_GID" >/dev/null; then
    groupadd -g "$USER_GID" "$USER_NAME"
fi

# 2. Create User if it doesn't exist
if ! id -u "$USER_UID" >/dev/null 2>&1; then
    useradd -u "$USER_UID" -g "$USER_GID" -m -s /bin/bash "$USER_NAME"
    echo "Created user $USER_NAME"
else
    # User exists (maybe root or pre-existing), just use the name associated with UID
    EXISTING_USER=$(getent passwd "$USER_UID" | cut -d: -f1)
    if [ "$EXISTING_USER" != "$USER_NAME" ]; then
        echo "WARNING: UID $USER_UID belongs to $EXISTING_USER, not $USER_NAME. Proceeding as $EXISTING_USER."
        USER_NAME=$EXISTING_USER
    fi
fi

HOME_DIR="/home/$USER_NAME"
mkdir -p "$HOME_DIR"

# 3. Fix Ownership of Home (Critical for PVCs)
chown "$USER_UID:$USER_GID" "$HOME_DIR"
cd "$HOME_DIR"

# 4. Populate Configs if empty (The Critical Fix for "Headless" or Broken Panel)
# We run this as the user to ensure permissions are correct
sudo -u "$USER_NAME" bash <<EOF
    # Basic dotfiles
    if [ ! -f "$HOME_DIR/.bashrc" ]; then
        echo "Populating .bashrc..."
        cp /etc/skel/.bashrc "$HOME_DIR/"
        cp /etc/skel/.profile "$HOME_DIR/"
    fi

    # VNC Setup
    mkdir -p "$HOME_DIR/.vnc"

    # Create xstartup script specifically for TigerVNC + XFCE
    cat > "$HOME_DIR/.vnc/xstartup" <<STARTUP
#!/bin/sh
unset SESSION_MANAGER
unset DBUS_SESSION_BUS_ADDRESS
while true; do
    /usr/bin/startxfce4
    echo "Desktop session ended. Restarting..."
    sleep 2
done
STARTUP
    chmod +x "$HOME_DIR/.vnc/xstartup"
EOF

# Set Password (as root to ensure PATH access, then chown)
mkdir -p "$HOME_DIR/.vnc"

VNCPASSWD_CMD=""
if [ -x "/usr/bin/vncpasswd" ]; then
    VNCPASSWD_CMD="/usr/bin/vncpasswd"
elif [ -x "/usr/bin/tigervncpasswd" ]; then
    VNCPASSWD_CMD="/usr/bin/tigervncpasswd"
else
    # Last ditch attempt: use `type` or search
    if command -v vncpasswd >/dev/null; then VNCPASSWD_CMD=$(command -v vncpasswd); fi
    if [ -z "$VNCPASSWD_CMD" ] && command -v tigervncpasswd >/dev/null; then VNCPASSWD_CMD=$(command -v tigervncpasswd); fi
fi

if [ -z "$VNCPASSWD_CMD" ]; then
    echo "ERROR: vncpasswd command not found! PATH: $PATH"
    # Debug: List what IS there
    ls -la /usr/bin/*vnc* || true
    exit 1
fi

echo "Setting VNC password using $VNCPASSWD_CMD..."
echo "$PASSWORD" | $VNCPASSWD_CMD -f > "$HOME_DIR/.vnc/passwd"
chmod 600 "$HOME_DIR/.vnc/passwd"
chown -R "$USER_UID:$USER_GID" "$HOME_DIR/.vnc"

# 5. Clean up stale locks (if pod restarted but PVC kept lock files)
rm -f /tmp/.X1-lock
rm -f /tmp/.X11-unix/X1
rm -f "$HOME_DIR/.vnc/*.pid"
rm -f "$HOME_DIR/.vnc/*.log"

# 6. Start VNC Server
echo "Starting TigerVNC server on :1..."
# usage: vncserver :1 -geometry 1280x800 -depth 24
sudo -u "$USER_NAME" vncserver :1 -geometry "$RESOLUTION" -depth "$DEPTH" -localhost no

# 7. Tail the log to keep container running
echo "VNC Started. Tailing logs..."
tail -f "$HOME_DIR/.vnc/"*.log
