#!/usr/bin/env bash
# Attic on this machine, all in one folder, ATTIC_HOME (<APPS_DIR>/attic of deploy.conf):
# service/ (this script, the releases and attic.jar pointing to the running one), data/ and
# backups/. Run by deploy.sh from <ATTIC_HOME>/service, and by hand there:
#
#   release.sh init                   prepare this machine, once: packages, start at boot, folders
#   release.sh receive <jar> <name>   add the jar as a release, without starting it
#   release.sh list                   the releases kept, newest first, * for the one in service
#   release.sh start [number|name]    install the service and run the newest release, or that one
#   release.sh stop                   stop Attic; it does not start at boot until started again
#   release.sh remove                 stop Attic and remove its service; releases and data stay
#   release.sh backup                 back up the data now, and print the backup's path
#   release.sh restore <backup>       replace the data with a backup, keeping the old data if it fails
#   release.sh summary [data dir]     what the data holds, in one line
#
# A release that does not start is left for the one that ran before. The newest
# ATTIC_KEEP_RELEASES releases are kept, and the one in service always.
#
# The settings come from deploy.sh as ATTIC_PORT, ATTIC_BACKUP_KEEP and ATTIC_KEEP_RELEASES, and are kept with
# the paths in service/attic.env for the services. The systemd units are copied, with ATTIC_HOME
# filled in, to ~/.config/systemd/user, where systemd finds them even before a disk is mounted.
set -euo pipefail

SERVICE_DIR="$(cd "$(dirname "$0")" && pwd)"
ATTIC_HOME="$(dirname "$SERVICE_DIR")"
RELEASES="$SERVICE_DIR/releases"
ENV_FILE="$SERVICE_DIR/attic.env"
UNIT_DIR="$HOME/.config/systemd/user"
JAVA_VERSION=25

# A setting: as given by deploy.sh, else as it was in attic.env, else the default
setting() {
    local name="$1" default="$2" value="${!1:-}"
    if [ -z "$value" ] && [ -f "$ENV_FILE" ]; then
        value="$(sed -n "s/^$name=//p" "$ENV_FILE" | tail -1)"
    fi
    echo "${value:-$default}"
}

load_env() {
    if [ ! -f "$ENV_FILE" ]; then
        echo "Attic is not installed here yet: deploy and start it first." >&2
        exit 1
    fi
    set -a
    # shellcheck source=/dev/null
    . "$ENV_FILE"
    set +a
}

# Names start with the build date and time, so the newest sorts first
releases() { find "$RELEASES" -maxdepth 1 -name 'attic-*.jar' -printf '%f\n' 2>/dev/null | sort -r; }

current() {
    if [ -L "$SERVICE_DIR/attic.jar" ]; then
        basename "$(readlink "$SERVICE_DIR/attic.jar")"
    fi
}

healthy() {
    echo -n "Starting $1" >&2
    for _ in $(seq 120); do
        if curl -fs -o /dev/null "http://127.0.0.1:${ATTIC_PORT:-8080}/actuator/health"; then
            echo " — running on http://127.0.0.1:${ATTIC_PORT:-8080}" >&2
            return 0
        fi
        echo -n "." >&2
        sleep 1
    done
    echo " — did not start. See: journalctl --user -u attic -n 50" >&2
    return 1
}

switch() {
    local name="$1" previous
    previous="$(current)"
    ln -sfn "releases/$name" "$SERVICE_DIR/attic.jar"
    systemctl --user restart attic.service
    if healthy "$name"; then
        return 0
    fi
    if [ -n "$previous" ] && [ "$previous" != "$name" ] && [ -f "$RELEASES/$previous" ]; then
        echo "Going back to $previous" >&2
        ln -sfn "releases/$previous" "$SERVICE_DIR/attic.jar"
        systemctl --user restart attic.service
        healthy "$previous" || true
    fi
    return 1
}

prune() {
    local keep="$1" running
    running="$(current)"
    releases | tail -n +$((keep + 1)) | while read -r old; do
        if [ "$old" != "$running" ]; then
            rm -f "$RELEASES/$old"
        fi
    done
}

java_version() { /usr/bin/java -version 2>&1 | grep -oP 'version "\K[0-9]+' || true; }

java_ok() {
    local version
    version="$(java_version)"
    [ -n "$version" ] && [ "$version" -ge "$JAVA_VERSION" ]
}

# The port is held by another program than Attic, e.g. another app: its answers would pass for Attic's
port_taken() {
    local pid listeners
    pid="$(systemctl --user show -p MainPID --value attic.service 2>/dev/null || true)"
    listeners="$(ss -ltnpH "sport = :$1" 2>/dev/null || true)"
    [ -n "$listeners" ] && ! grep -q "pid=${pid:-none}," <<< "$listeners"
}

check_java() {
    if ! java_ok; then
        local found
        found="$(java_version)"
        echo "Attic needs Java $JAVA_VERSION or later at /usr/bin/java (found: ${found:-none})." >&2
        echo "Run ./deploy/deploy.sh init on your computer to install it." >&2
        exit 1
    fi
}

# Everything Attic needs here besides its releases, safe to run again: it does only what is missing
init() {
    local arch missing=()
    arch="$(uname -m)"
    if [ "$arch" != aarch64 ] && [ "$arch" != x86_64 ]; then
        echo "This is a $arch system, and Java $JAVA_VERSION needs a 64-bit one (on a Raspberry Pi, the 64-bit Raspberry Pi OS)." >&2
        exit 1
    fi

    java_ok || missing+=("openjdk-$JAVA_VERSION-jre-headless")
    command -v python3 > /dev/null || missing+=(python3)
    command -v curl > /dev/null || missing+=(curl)
    if [ ${#missing[@]} -gt 0 ]; then
        echo "Installing ${missing[*]} (sudo asks for your password)…"
        sudo apt-get update -qq
        if [[ " ${missing[*]} " == *openjdk* ]] && ! apt-cache show "openjdk-$JAVA_VERSION-jre-headless" > /dev/null 2>&1; then
            echo "Java $JAVA_VERSION is not in the packages of this system. Install Temurin $JAVA_VERSION from" >&2
            echo "https://adoptium.net/installation/linux/ and run init again." >&2
            exit 1
        fi
        sudo apt-get install -y -qq "${missing[@]}"
    fi
    check_java
    echo "Java $(java_version), python3 and curl: ok"

    # Run the services at boot, before anybody logs in
    if [ "$(loginctl show-user "$USER" -p Linger --value 2>/dev/null)" != "yes" ]; then
        loginctl enable-linger "$USER" 2> /dev/null || sudo loginctl enable-linger "$USER"
    fi
    echo "Attic starts at boot: ok"

    mkdir -p "$RELEASES"
    # ID documents: readable by this user only
    mkdir -p -m 700 "$ATTIC_HOME/data" "$ATTIC_HOME/backups"
    echo "Folders in $ATTIC_HOME: ok"
}

# A release, from a number of the list, a name, or the newest when none is given
pick() {
    local choice="${1:-1}" name
    if [[ "$choice" =~ ^[0-9]+$ ]]; then
        name="$(releases | sed -n "${choice}p")"
    else
        name="$choice"
    fi
    if [ -z "$(releases)" ]; then
        echo "No release here yet: run ./deploy/deploy.sh deploy on your computer first." >&2
        exit 1
    fi
    if [ -z "$name" ] || [ ! -f "$RELEASES/$name" ]; then
        echo "No such release: $choice. See: release.sh list" >&2
        exit 1
    fi
    echo "$name"
}

receive() {
    local jar="$1" name="$2"
    mkdir -p "$RELEASES"
    if [ "$(realpath "$jar")" != "$(realpath -m "$RELEASES/$name")" ]; then
        install -m 644 "$jar" "$RELEASES/$name"
    fi
    prune "$(setting ATTIC_KEEP_RELEASES 3)"
    echo "Received $name; it runs once started" >&2
}

# When the data of a directory last changed, in seconds; an empty write-ahead log only means the
# database was opened
changed() {
    python3 - "$1" <<'PY'
import os, sys
database = os.path.join(sys.argv[1], 'attic.db')
wal = database + '-wal'
times = [os.path.getmtime(database)] if os.path.exists(database) else [0]
if os.path.exists(wal) and os.path.getsize(wal) > 0:
    times.append(os.path.getmtime(wal))
print(int(max(times)))
PY
}

start_release() {
    local name backup_keep keep old_home
    check_java
    name="$(pick "${1:-}")"
    ATTIC_PORT="$(setting ATTIC_PORT 8080)"
    backup_keep="$(setting ATTIC_BACKUP_KEEP 30)"
    keep="$(setting ATTIC_KEEP_RELEASES 3)"
    # Where the service ran until now, from its unit
    old_home="$(sed -n 's#^EnvironmentFile=\(.*\)/service/attic.env$#\1#p' "$UNIT_DIR/attic.service" 2>/dev/null || true)"

    # Another APPS_DIR in deploy.conf would start Attic on an empty database
    if [ -n "$old_home" ] && [ "$old_home" != "$ATTIC_HOME" ] && [ -f "$old_home/data/attic.db" ] \
        && [ ! -f "$ATTIC_HOME/data/attic.db" ]; then
        echo "The data is in $old_home, but deploy.conf now says $ATTIC_HOME. Move it first, on this machine:" >&2
        echo "    systemctl --user stop attic && mv \"$old_home/data\" \"$old_home/backups\" \"$ATTIC_HOME/\"" >&2
        echo "then start again; $old_home/service can be deleted after." >&2
        exit 1
    fi

    if port_taken "$ATTIC_PORT"; then
        echo "Port $ATTIC_PORT is used by another program there: choose another APP_PORT in deploy.conf." >&2
        exit 1
    fi

    mkdir -p "$UNIT_DIR"
    # ID documents: readable by this user only
    mkdir -p -m 700 "$ATTIC_HOME/data" "$ATTIC_HOME/backups"
    # The units with this ATTIC_HOME, leaving the comments as they are
    for unit in attic.service attic-backup.service attic-backup.timer; do
        sed "/^#/!s#@ATTIC_HOME@#$ATTIC_HOME#g" "$SERVICE_DIR/$unit" > "$UNIT_DIR/$unit"
    done

    cat > "$ENV_FILE" <<ENV
# Written by release.sh on every start, from deploy/deploy.conf: change it there
# Where Attic keeps its database, pictures, shared folder and sign-in log
ATTIC_DATA=$ATTIC_HOME/data
# The port Attic listens on
ATTIC_PORT=$ATTIC_PORT
# Where the daily backups go, and how many to keep
ATTIC_BACKUP_DIR=$ATTIC_HOME/backups
ATTIC_BACKUP_KEEP=$backup_keep
# How many releases to keep for going back
ATTIC_KEEP_RELEASES=$keep
ENV

    systemctl --user daemon-reload
    systemctl --user enable --quiet attic.service attic-backup.timer
    systemctl --user start attic-backup.timer
    switch "$name" || exit 1
    echo "Attic is in $ATTIC_HOME" >&2
    if [ "$(loginctl show-user "$USER" -p Linger --value 2>/dev/null)" != "yes" ]; then
        echo "Attic stops when you log out. To run it at boot: loginctl enable-linger $USER" >&2
    fi
}

stop_service() {
    systemctl --user disable --now --quiet attic.service
    echo "Attic is stopped, and does not start at boot until started again." >&2
}

# The service goes; the releases, the data and the backups stay, for a start later
remove_service() {
    systemctl --user disable --now --quiet attic.service attic-backup.timer 2> /dev/null || true
    rm -f "$UNIT_DIR/attic.service" "$UNIT_DIR/attic-backup.service" "$UNIT_DIR/attic-backup.timer" "$SERVICE_DIR/attic.jar"
    systemctl --user daemon-reload
    echo "Attic's service is removed. Its releases, data and backups are still in $ATTIC_HOME:" >&2
    echo "start it again with ./deploy/deploy.sh start, or delete that folder to remove Attic entirely." >&2
}

list() {
    local running state
    running="$(current)"
    state="$(systemctl --user is-active attic.service 2>/dev/null || true)"
    releases | nl -w2 -s'  ' | while read -r number name; do
        if [ "$name" = "$running" ]; then
            echo "* $number  $name  ($([ "$state" = active ] && echo running || echo stopped))"
        else
            echo "  $number  $name"
        fi
    done
}

# The backup's path on stdout, for deploy.sh to fetch; what backup.py says on stderr
backup() {
    load_env
    python3 "$SERVICE_DIR/backup.py" >&2
    find "$ATTIC_BACKUP_DIR" -maxdepth 1 -name 'attic-*.tar.gz' | sort | tail -1
}

restore() {
    local archive="$1" aside was_active
    load_env
    was_active="$(systemctl --user is-active attic.service 2>/dev/null || true)"
    archive="$(realpath "$archive")"
    aside="$ATTIC_DATA.before-$(date +%Y%m%d-%H%M%S)"
    if [ -f "$ATTIC_DATA/attic.db" ]; then
        echo "Backing up the data here first…" >&2
        backup > /dev/null
    fi
    systemctl --user stop attic.service
    if [ -e "$ATTIC_DATA" ]; then
        mv "$ATTIC_DATA" "$aside"
    fi
    mkdir -p -m 700 "$ATTIC_DATA"
    tar -xzf "$archive" -C "$ATTIC_DATA"
    systemctl --user start attic.service
    if healthy "with the new data"; then
        rm -rf "$aside"
        rm -f "$archive"
        # Checked by starting it; stopped again when it was
        if [ "$was_active" != active ]; then
            systemctl --user stop attic.service
        fi
        return 0
    fi
    if [ -e "$aside" ]; then
        echo "Putting the old data back" >&2
        systemctl --user stop attic.service
        rm -rf "$ATTIC_DATA"
        mv "$aside" "$ATTIC_DATA"
        systemctl --user start attic.service
        healthy "with the old data" || true
    fi
    exit 1
}

summary() {
    local dir="${1:-}"
    if [ -z "$dir" ]; then
        load_env
        dir="$ATTIC_DATA"
    fi
    if [ ! -f "$dir/attic.db" ]; then
        echo "no data yet"
        return 0
    fi
    python3 - "$dir" "$(changed "$dir")" <<'PY'
import datetime, os, sqlite3, sys
database = os.path.join(sys.argv[1], 'attic.db')
connection = sqlite3.connect(f'file:{database}?mode=ro', uri=True)
count = lambda table: connection.execute(f'SELECT count(*) FROM {table}').fetchone()[0]
print(f"{count('catalog_item')} catalog items, {count('inventory_item')} inventory lines, {count('heir')} heirs,"
      f" last changed {datetime.datetime.fromtimestamp(int(sys.argv[2])):%Y-%m-%d %H:%M}")
PY
}

case "${1:-}" in
    init) init ;;
    receive) receive "$2" "$3" ;;
    list) list ;;
    start) start_release "${2:-}" ;;
    stop) stop_service ;;
    remove) remove_service ;;
    backup) backup ;;
    restore) restore "$2" ;;
    summary) summary "${2:-}" ;;
    *) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2 ;;
esac
