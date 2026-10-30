#!/usr/bin/env bash
# Build Attic here and run it on the machine of deploy/deploy.conf, reached over SSH, which needs
# only Java:
#
#   deploy.sh init             prepare the machine, once: packages, start at boot, folders
#   deploy.sh deploy           build (with tests) and send it there as a new release, not started
#   deploy.sh list             the releases kept there, * for the one in service
#   deploy.sh start [number]   run the newest release there, or the one of that number in list
#   deploy.sh stop             stop Attic there; it does not start at boot until started again
#   deploy.sh remove           remove Attic's service there; its releases and data stay
#   deploy.sh data-to-pi       replace the data there with this computer's data/ (asks first)
#   deploy.sh data-from-pi     replace this computer's data/ with the data there (asks first)
#
# Without a command, this list. See release.sh for what happens on the other machine.
set -euo pipefail

usage() { sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//' >&2; }

# Nothing is done without a command, nor before deploy.conf is read
case "${1:-}" in
    init|deploy|list|start|stop|remove|data-to-pi|data-from-pi) ;;
    '') usage; exit 0 ;;
    *) echo "Unknown command: $1" >&2; usage; exit 2 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONF="$ROOT/deploy/deploy.conf"
# The data of ./mvnw spring-boot:run
LOCAL_DATA="$ROOT/data"

if [ ! -f "$CONF" ]; then
    echo "No $CONF: copy deploy/deploy.conf.example to deploy/deploy.conf and fill in the machine." >&2
    exit 1
fi
HOST="" USER_NAME="" SSH_PORT="" APP_PORT="" APPS_DIR="" BACKUP_KEEP="" KEEP_RELEASES="" LOCAL_BACKUP_DIR=""
# shellcheck source=deploy.conf.example
. "$CONF"
if [ -z "$HOST" ]; then
    echo "HOST is not set in $CONF" >&2
    exit 1
fi
LOCAL_BACKUP_DIR="${LOCAL_BACKUP_DIR:-$HOME/attic-backups-pi}"
# Attic's folder there, among the other apps; relative paths are from the home there
ATTIC_HOME="${APPS_DIR:-apps}/attic"
SERVICE="$ATTIC_HOME/service"

# The same options for ssh and scp, which spell the port differently on their command lines
SSH_OPTS=()
[ -n "$USER_NAME" ] && SSH_OPTS+=(-o "User=$USER_NAME")
[ -n "$SSH_PORT" ] && SSH_OPTS+=(-o "Port=$SSH_PORT")

remote() { ssh "${SSH_OPTS[@]}" "$HOST" "$@"; }

# Before anything is sent: a folder under /mnt or /media is meant to be on a disk, and when that
# disk is not mounted Attic would quietly write to the SD card instead
check_mounted() {
    remote bash -s -- "$(printf %q "$ATTIC_HOME")" <<'CHECK'
dir="$1" existing="$1"
case "$dir" in /mnt/*|/media/*) ;; *) exit 0 ;; esac
while [ ! -e "$existing" ]; do existing="$(dirname "$existing")"; done
if [ "$(findmnt -no TARGET -T "$existing")" = "/" ]; then
    echo "$dir is not on a mounted disk there: mount it first, or change APPS_DIR in deploy.conf." >&2
    exit 1
fi
CHECK
}

# The scripts and services, sent with every command that runs them there
send_tools() {
    check_mounted
    remote mkdir -p "$SERVICE/releases"
    scp -q "${SSH_OPTS[@]}" "$ROOT"/deploy/release.sh "$ROOT"/deploy/backup.py "$ROOT"/deploy/attic.service \
        "$ROOT"/deploy/attic-backup.service "$ROOT"/deploy/attic-backup.timer "$HOST:$SERVICE/"
}

confirm() {
    local answer
    read -rp "$1 Type yes to go on: " answer
    if [ "$answer" != "yes" ]; then
        echo "Nothing changed." >&2
        exit 1
    fi
}

deploy() {
    # When it was built and from which commit, e.g. attic-20261029-230412-fa78886.jar, and
    # -uncommitted at the end when changes not committed yet went along
    local commit name
    commit="$(git -C "$ROOT" rev-parse --short HEAD)"
    if [ -n "$(git -C "$ROOT" status --porcelain)" ]; then
        commit="$commit-uncommitted"
        echo "Changes not committed yet go along in this release." >&2
    fi
    name="attic-$(date +%Y%m%d-%H%M%S)-$commit.jar"

    echo "Building (with tests)…"
    (cd "$ROOT" && ./mvnw -q clean package)

    echo "Sending $name to $HOST…"
    send_tools
    scp -q "${SSH_OPTS[@]}" "$ROOT"/target/attic-*.jar "$HOST:$SERVICE/releases/$name"
    remote "$(settings) $SERVICE/release.sh receive $SERVICE/releases/$name $name"
    echo "To run it there: ./deploy/deploy.sh start"
}

# The settings of deploy.conf, quoted for the shell there; an empty one keeps the one there
settings() {
    printf 'ATTIC_PORT=%q ATTIC_BACKUP_KEEP=%q ATTIC_KEEP_RELEASES=%q' "$APP_PORT" "$BACKUP_KEEP" "$KEEP_RELEASES"
}

data_to_pi() {
    local tmp archive
    if [ ! -f "$LOCAL_DATA/attic.db" ]; then
        echo "No data on this computer: $LOCAL_DATA/attic.db does not exist." >&2
        exit 1
    fi
    if ! remote test -f "$SERVICE/attic.env"; then
        echo "Attic is not on $HOST yet: run ./deploy/deploy.sh init, deploy and start." >&2
        exit 1
    fi
    send_tools
    echo "This computer: $("$ROOT/deploy/release.sh" summary "$LOCAL_DATA")"
    echo "$HOST: $(remote "$SERVICE/release.sh" summary)"
    confirm "Replace the data on $HOST with this computer's? The data there is backed up first."

    # A consistent copy, even while ./mvnw spring-boot:run writes to it
    tmp="$(mktemp -d)"
    # shellcheck disable=SC2064 # tmp is local: expanded now, not at exit
    trap "rm -rf '$tmp'" EXIT
    ATTIC_DATA="$LOCAL_DATA" ATTIC_BACKUP_DIR="$tmp" ATTIC_BACKUP_KEEP=0 python3 "$ROOT/deploy/backup.py" > /dev/null
    archive="$(find "$tmp" -name 'attic-*.tar.gz')"
    echo "Sending the data to $HOST…"
    scp -q "${SSH_OPTS[@]}" "$archive" "$HOST:$SERVICE/incoming.tar.gz"
    remote "$SERVICE/release.sh" restore "$SERVICE/incoming.tar.gz"
}

data_from_pi() {
    local path archive
    # ./mvnw spring-boot:run would keep the old database open
    if ss -ltn 2>/dev/null | grep -qE ':8080\b'; then
        echo "Something runs on port 8080 here, likely ./mvnw spring-boot:run: stop it first." >&2
        exit 1
    fi
    send_tools
    echo "Backing up on $HOST…"
    path="$(remote "$SERVICE/release.sh" backup)"
    # Kept here too: a copy of the Pi's data off its SD card
    mkdir -p -m 700 "$LOCAL_BACKUP_DIR"
    scp -q "${SSH_OPTS[@]}" "$HOST:$path" "$LOCAL_BACKUP_DIR/"
    archive="$LOCAL_BACKUP_DIR/$(basename "$path")"
    echo "Saved $archive"

    echo "$HOST: $(remote "$SERVICE/release.sh" summary)"
    echo "This computer: $("$ROOT/deploy/release.sh" summary "$LOCAL_DATA")"
    confirm "Replace this computer's data/ with the data of $HOST? The old one is kept as data.previous."
    rm -rf "$ROOT/data.previous"
    if [ -e "$LOCAL_DATA" ]; then
        mv "$LOCAL_DATA" "$ROOT/data.previous"
    fi
    mkdir -m 700 "$LOCAL_DATA"
    tar -xzf "$archive" -C "$LOCAL_DATA"
    echo "data/ now holds the data of $HOST."
}

case "$1" in
    deploy) deploy ;;
    init) send_tools && exec ssh -t "${SSH_OPTS[@]}" "$HOST" "$(settings) $SERVICE/release.sh init" ;;
    list|stop|remove) exec ssh "${SSH_OPTS[@]}" "$HOST" "$SERVICE/release.sh" "$1" ;;
    start) send_tools && exec ssh -t "${SSH_OPTS[@]}" "$HOST" "$(settings) $SERVICE/release.sh start $(printf %q "${2:-}")" ;;
    data-to-pi) data_to_pi ;;
    data-from-pi) data_from_pi ;;
esac
