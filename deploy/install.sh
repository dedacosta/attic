#!/usr/bin/env bash
# Build Attic and (re)install it as a systemd user service, with a daily backup.
# Run again after changing the code to update the running service.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="$HOME/.local/share/attic"
CONFIG_DIR="$HOME/.config/attic"
UNIT_DIR="$HOME/.config/systemd/user"

# Another Attic (e.g. "./mvnw spring-boot:run") would hold the port and lose its classes to "clean"
SERVICE_PID="$(systemctl --user show -p MainPID --value attic.service 2>/dev/null || true)"
OTHER="$(ss -ltnp 2>/dev/null | grep -E ':8080\b' | grep -v "pid=${SERVICE_PID:-none}," || true)"
if [ -n "$OTHER" ]; then
    PID="$(grep -oP 'pid=\K[0-9]+' <<< "$OTHER" | head -1)"
    echo "Port 8080 is used by another program, so Attic cannot be installed now:" >&2
    ps -o pid=,args= -p "$PID" 2>/dev/null | cut -c1-120 | sed 's/^/    /' >&2
    if ps -o args= -p "$PID" 2>/dev/null | grep -q 'target/classes'; then
        echo "That is a development run (./mvnw spring-boot:run). Stop it with Ctrl+C in its terminal, then run this again." >&2
    else
        echo "Stop that program, then run this again." >&2
    fi
    exit 1
fi

echo "Building (with tests)…"
(cd "$ROOT" && ./mvnw -q clean package)

mkdir -p "$APP_DIR" "$CONFIG_DIR" "$UNIT_DIR"
install -m 644 "$ROOT"/target/attic-*.jar "$APP_DIR/attic.jar"
install -m 755 "$ROOT/deploy/backup.py" "$APP_DIR/backup.py"
install -m 644 "$ROOT"/deploy/attic.service "$ROOT"/deploy/attic-backup.service "$ROOT"/deploy/attic-backup.timer "$UNIT_DIR/"

if [ ! -f "$CONFIG_DIR/attic.env" ]; then
    cat > "$CONFIG_DIR/attic.env" <<ENV
# Where Attic keeps its database and pictures
ATTIC_DATA=$ROOT/data
# Where the daily backups go, and how many to keep
ATTIC_BACKUP_DIR=$HOME/attic-backups
ATTIC_BACKUP_KEEP=30
ENV
    echo "Created $CONFIG_DIR/attic.env"
fi

systemctl --user daemon-reload
systemctl --user enable --quiet attic.service attic-backup.timer
systemctl --user restart attic.service
systemctl --user start attic-backup.timer

echo -n "Starting"
for _ in $(seq 60); do
    if curl -fs -o /dev/null http://127.0.0.1:8080/actuator/health; then
        echo " — Attic is running on http://127.0.0.1:8080"
        exit 0
    fi
    echo -n "."
    sleep 1
done
echo
echo "Attic did not start. See: journalctl --user -u attic -n 50" >&2
exit 1
