#!/bin/sh
systemctl --user stop attic-backup.timer
systemctl --user stop attic-backup.service
systemctl --user stop attic.service
rm ~/.config/systemd/user/attic-backup.service
rm ~/.config/systemd/user/attic-backup.timer
rm ~/.config/systemd/user/attic.service
systemctl --user daemon-reload