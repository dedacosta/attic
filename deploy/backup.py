#!/usr/bin/env python3
"""Back up Attic into one file, attic-<date>.tar.gz: a consistent copy of the database plus the
picture files. Keeps the newest ATTIC_BACKUP_KEEP backups (default 30).

Settings come from the environment: ATTIC_DATA (the data directory with attic.db and pictures/),
ATTIC_BACKUP_DIR (where backups go) and ATTIC_BACKUP_KEEP.
"""
import datetime
import os
import sqlite3
import sys
import tarfile
import tempfile
from pathlib import Path

data = Path(os.environ['ATTIC_DATA']).expanduser()
target = Path(os.environ.get('ATTIC_BACKUP_DIR', '~/attic-backups')).expanduser()
keep = int(os.environ.get('ATTIC_BACKUP_KEEP', '30'))

# Backups contain ID documents: readable by this user only
os.umask(0o077)

database = data / 'attic.db'
if not database.exists():
    sys.exit(f'No database at {database}')
target.mkdir(mode=0o700, parents=True, exist_ok=True)
target.chmod(0o700)
name = f'attic-{datetime.datetime.now():%Y-%m-%d-%H%M}.tar.gz'

with tempfile.TemporaryDirectory() as tmp:
    # SQLite's backup API gives a consistent copy even while Attic is writing
    snapshot = Path(tmp) / 'attic.db'
    source = sqlite3.connect(f'file:{database}?mode=ro', uri=True)
    destination = sqlite3.connect(snapshot)
    with destination:
        source.backup(destination)
    destination.close()
    source.close()

    # Pictures after the database: files are never changed, only added and removed
    partial = target / (name + '.partial')
    with tarfile.open(partial, 'w:gz') as archive:
        archive.add(snapshot, arcname='attic.db')
        if (data / 'pictures').is_dir():
            archive.add(data / 'pictures', arcname='pictures')
    partial.rename(target / name)

backups = sorted(target.glob('attic-*.tar.gz'))
for old in backups[:-keep] if keep > 0 else []:
    old.unlink()

size = (target / name).stat().st_size
print(f'Backed up to {target / name} ({size / 1024 / 1024:.1f} MB), {min(len(backups), keep)} backups kept')
