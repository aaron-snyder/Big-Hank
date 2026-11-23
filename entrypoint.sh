#!/bin/sh
# Hosts like Railway mount the data volume owned by root. If we start as root, hand the data folder to the
# unprivileged "hank" user, then drop root before running anything else. The bot itself never runs as root.
set -e
if [ "$(id -u)" = "0" ]; then
    mkdir -p /app/data
    chown -R hank:hank /app/data
    exec setpriv --reuid=hank --regid=hank --init-groups -- "$@"
fi
exec "$@"
