# Running Attic on a Raspberry Pi

Attic runs on a Raspberry Pi (or any Linux machine with Java) as a systemd **user** service and
listens on `APP_PORT` (8080 by default). How your devices reach it is the Pi's own setup, e.g.
Tailscale for HTTPS from anywhere (see [Tailscale on the Pi](#tailscale-on-the-pi)).

The Pi gets no source code: the jar is built on your computer and sent over SSH. On your computer
Attic only runs for development, with `./mvnw spring-boot:run` and its own `data/`.

## Set up the Pi (one time)

The Pi needs a 64-bit Raspberry Pi OS and to be reachable with `ssh`. Tell `deploy.sh` where it
is in `deploy/deploy.conf` (kept out of git):

```bash
cp deploy/deploy.conf.example deploy/deploy.conf   # then set HOST, and USER_NAME, SSH_PORT, APP_PORT if needed
```

`HOST` is an IP address, a hostname, the Pi's Tailscale name or a `Host` of `~/.ssh/config`.
Then prepare the Pi from your computer:

```bash
./deploy/deploy.sh init
```

It installs what is missing (Java 25, `python3`, `curl`; `sudo` asks for the Pi's password), lets
Attic start at boot (`loginctl enable-linger`) and creates the folders below. It does only what is
missing, so run it again any time. If the Pi's system has no Java 25 package, it says so: install
Temurin 25 from https://adoptium.net then. How the Pi is reached from your devices, e.g. with
Tailscale, is the Pi's own setup and not part of the scripts: see [Tailscale](#tailscale-on-the-pi).

## Deploy and start

From the project on your computer, for every new version:

```bash
./deploy/deploy.sh deploy      # build (with tests) and send it to the Pi as a new release
./deploy/deploy.sh start       # run the newest release there
```

`deploy` only delivers: the Attic in service keeps running until you `start`. `start` installs
the `attic` service and the daily backup (the first time, or after `remove`), switches to the
release and checks that it answers. A release that does not answer is left for the one that ran
before.

```bash
./deploy/deploy.sh stop        # stop Attic; it does not start at boot until started again
./deploy/deploy.sh remove      # remove its service; releases, data and backups stay
```

After `remove`, `start` installs the service again. To remove Attic entirely, delete its folder on
the Pi as well.

On the Pi Attic is all in one folder, next to your other apps (`APPS_DIR`, `~/apps` by default):

```
~/apps/attic/
├── service/   releases/, attic.jar (the one in service), release.sh, backup.py, attic.env
├── data/      attic.db, pictures/, folder/ (the shared folder), logs/ (who signed in)
└── backups/   attic-<date>.tar.gz, one a day
```

Only the systemd units are elsewhere, in `~/.config/systemd/user/`, where systemd looks for them.

## Going back to an earlier release

Every deploy is a release named after when it was built and its commit, e.g.
`attic-20261029-230412-fa78886.jar`; `-uncommitted` at the end means changes not committed yet
went along. The
newest three are kept, and the one in service always.

```bash
./deploy/deploy.sh list        # the releases on the Pi, * for the one in service
./deploy/deploy.sh start 2     # run the second newest
./deploy/deploy.sh start       # back to the newest
```

On the Pi itself: `~/apps/attic/service/release.sh list`, `… start 2`, `… stop`, `… remove`.

The database is not part of a release: when a newer release has changed it, an older release
may not start with it. Restore the backup from before the update then.

## Settings

All in `deploy/deploy.conf` on your computer (see `deploy.conf.example`). Every `start` writes them
to `~/apps/attic/service/attic.env` on the Pi, so change them in `deploy.conf` and start again.

| Setting | Default | Meaning |
|---|---|---|
| `HOST`, `USER_NAME`, `SSH_PORT` | — | How to reach the Pi with `ssh` |
| `APP_PORT` | `8080` | The port Attic listens on, one of its own among your apps; after changing it, also in `tailscale serve` on the Pi |
| `APPS_DIR` | `~/apps` | The folder of your apps on the Pi; Attic is in `<APPS_DIR>/attic` — ideally a USB disk, not the SD card |
| `BACKUP_KEEP` | `30` | Number of backups to keep |
| `KEEP_RELEASES` | `3` | Number of releases to keep for going back |
| `LOCAL_BACKUP_DIR` | `~/attic-backups-pi` | Where `data-from-pi` keeps the backups it fetches, on your computer |

`APPS_DIR` is absolute (`/home/pi/apps`, `/mnt/usb/apps`): a `~` would be your home on your
computer. Under `/mnt` or `/media` it must be on a mounted disk, or the deploy refuses: otherwise
Attic would write to the SD card while the disk is missing. When `APPS_DIR` changes and the data is
still in the old place, `start` refuses and says how to move it.

## Moving the data between your computer and the Pi

```bash
./deploy/deploy.sh data-to-pi      # your computer's data/ → the Pi
./deploy/deploy.sh data-from-pi    # the Pi → your computer's data/
```

**data-to-pi** replaces the data on the Pi, normally once, after the first deploy. It shows what
both sides hold (items, heirs, when they last changed) and asks first. Then it backs up the Pi,
sends a consistent copy of `data/` (even while `./mvnw spring-boot:run` runs), and starts Attic
with it. If Attic does not start, the old data is put back.

**data-from-pi** replaces your computer's `data/` with the Pi's, to develop with the real data.
Stop `./mvnw spring-boot:run` first. Your old `data/` is kept as `data.previous/`, and the backup
fetched stays in `LOCAL_BACKUP_DIR` — a copy of the Pi's data off its SD card.

## Tailscale on the Pi

Done by hand on the Pi, once; the scripts do not touch it. Install Tailscale and sign in:

```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up                     # opens a link to sign in to Tailscale
sudo tailscale set --operator=$USER   # lets you run "tailscale serve" without sudo
```

In the Tailscale admin console (https://login.tailscale.com/admin/dns), turn on **MagicDNS** and
**HTTPS Certificates**. Then serve Attic on HTTPS, with its `APP_PORT`:

```bash
tailscale serve --bg <APP_PORT>
```

It prints the address, like `https://<pi>.<tailnet>.ts.net`. The setting is kept across restarts;
`tailscale serve status` shows it again and `tailscale serve reset` removes it. `tailscale funnel`
instead of `serve` would open Attic to the whole internet, not only to your devices.

The address stays the same across restarts of the Pi, the phone and Attic. It changes only
when the Pi gets another Tailscale name, so:

- in the admin console (Machines → the Pi → …), choose **Disable key expiry**, or Tailscale
  asks to sign in again after 180 days;
- do not rename the Pi there, and do not run `tailscale logout` (signing in again can
  register it as a new machine with a new name);
- after a rename anyway: `tailscale serve reset && tailscale serve --bg 8080` on the Pi.

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

## Who signed in

Attic keeps a log of everybody who gets in and everybody who tries, in the `logs` folder of the
data directory (`~/apps/attic/data/logs`). It is for the super-administrator: there is
no screen for it, and the files are readable only by you.

- `sign-ins-2026-10.log` is the current month, a line per event, the newest last.
- A month that is over is compressed: `sign-ins-2026-09.log.gz`.

```bash
tail -f ~/apps/attic/data/logs/sign-ins-$(date +%Y-%m).log    # watch as it happens
zcat ~/apps/attic/data/logs/sign-ins-2026-09.log.gz           # a past month
zgrep -h WRONG_PASSWORD ~/apps/attic/data/logs/*.gz  # every wrong password of the past months
```

Each line has five columns separated by tabs: when, what, the username, the IP address it came from
(through Tailscale, the device's Tailscale address) and the browser.

| What | Meaning |
|---|---|
| `SIGNED_IN` | Signed in with username and password |
| `SIGNED_IN_AUTOMATICALLY` | Signed in again by "stay signed in", without typing anything |
| `WRONG_PASSWORD` | An existing username with a wrong password |
| `UNKNOWN_USER` | A username that no account has, written as it was typed |
| `SIGN_IN_REFUSED` | Refused for another reason |
| `SIGNED_OUT` | Signed out |
| `ACCOUNT_CREATED` | The first account was set up |
| `REGISTERED` | An account was created with an invitation |
| `REGISTRATION_REFUSED` | Somebody tried to register without a valid invitation |

Passwords are never written. Nothing is deleted: a year of sign-ins is a few kilobytes.

## Backups and restoring

Every day `backup.py` writes `attic-<date>.tar.gz` with a consistent copy of the database, all
pictures, the shared folder and the sign-in log, readable only by you. `./deploy/deploy.sh data-from-pi` brings one
to your computer; copy them to another disk or cloud storage now and then too: a backup on the
same disk does not survive a broken disk.

To restore one:

```bash
cd ~/apps/attic                                   # <APPS_DIR>/attic
systemctl --user stop attic
mv data data.broken                               # keep the old data until all is well
mkdir data && tar -xzf backups/attic-<date>.tar.gz -C data
systemctl --user start attic
```
