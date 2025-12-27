FROM debian:bookworm-slim

# Install XFCE, TigerVNC, and utilities
# --no-install-recommends key to keeping it small
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    xfce4 \
    xfce4-goodies \
    tigervnc-standalone-server \
    tigervnc-common \
    tigervnc-tools \
    dbus-x11 \
    x11-xserver-utils \
    xfonts-base \
    net-tools \
    procps \
    vim \
    curl \
    ca-certificates \
    sudo \
    gnupg \
    libreoffice \
    && curl -fsSL https://dl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update && apt-get install -y google-chrome-stable \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Create a directory for our startup scripts
WORKDIR /opt/startup
COPY entrypoint.sh /opt/startup/entrypoint.sh
RUN chmod +x /opt/startup/entrypoint.sh

# Set default environment variables
ENV VNC_PORT=5901 \
    VNC_COL_DEPTH=24 \
    VNC_RESOLUTION=1280x800 \
    VNC_PW=guacamole

# Expose VNC port
EXPOSE 5901

ENTRYPOINT ["/opt/startup/entrypoint.sh"]
