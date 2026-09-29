# Running Attic at home, reachable through Tailscale

Attic runs on this computer as a systemd **user** service and listens only on
`127.0.0.1:8080`. Tailscale makes it reachable from your own devices, anywhere, over HTTPS —
Attic itself is never exposed to the internet or the local Wi-Fi.

## Install or update Attic

```bash
./deploy/install.sh
```

Builds Attic (running all tests), copies it to `~/.local/share/attic/`, and (re)starts the
`attic` service and the daily backup. Run it again after every code change. Stop any
`./mvnw spring-boot:run` first — both want port 8080.

Settings live in `~/.config/attic/attic.env` (created on the first install):

| Setting | Default | Meaning |
|---|---|---|
| `ATTIC_DATA` | `<project>/data` | Database and pictures |
| `ATTIC_BACKUP_DIR` | `~/attic-backups` | Where the daily backups go — ideally another disk |
| `ATTIC_BACKUP_KEEP` | `30` | Number of backups to keep |

After editing it: `systemctl --user restart attic`.

## Tailscale (one time)

On this computer:

```bash
sudo tailscale set --operator=$USER   # lets you run "tailscale serve" without sudo
sudo tailscale up                     # opens a link to sign in to Tailscale
loginctl enable-linger $USER          # run Attic at boot, even before you log in
```

In the Tailscale admin console (https://login.tailscale.com/admin/dns), turn on **MagicDNS** and
**HTTPS Certificates**. Then:

```bash
tailscale serve --bg 8080
```

It prints the address, like `https://<computer>.<tailnet>.ts.net`. The setting is kept across
restarts. `tailscale serve status` shows it again; `tailscale serve reset` removes it.

The address stays the same across restarts of the computer, the phone and Attic. It changes only
when the computer gets another Tailscale name, so:

- in the admin console (Machines → this computer → …), choose **Disable key expiry**, or Tailscale
  asks to sign in again after 180 days;
- do not rename the computer there, and do not run `tailscale logout` (signing in again can
  register it as a new machine with a new name);
- after a rename anyway: `tailscale serve reset && tailscale serve --bg 8080`.

On the iPhone: install the **Tailscale** app, sign in with the same account, open the address in
Safari and sign in to Attic. *Share → Add to Home Screen* makes it look like an app.

## Everyday commands

```bash
systemctl --user status attic              # is it running?
journalctl --user -u attic -f              # its log
systemctl --user restart attic
systemctl --user start attic-backup        # back up now
systemctl --user list-timers attic-backup  # when is the next backup?
```

## Backups and restoring

Every day `backup.py` writes `attic-<date>.tar.gz` with a consistent copy of the database and all
pictures, readable only by you. Copy them to another disk or cloud storage now and then: a backup
on the same disk does not survive a broken disk.

To restore one:

```bash
systemctl --user stop attic
mv data data.broken                               # keep the old data until all is well
mkdir data && tar -xzf ~/attic-backups/attic-<date>.tar.gz -C data
systemctl --user start attic
```

## Developing next to the service

The service owns port 8080. To work on the code: `systemctl --user stop attic`, develop with
`./mvnw spring-boot:run` and `npm run dev` as before, then `./deploy/install.sh` to put the new
version into service. Development and the service use the same `data/` directory.
