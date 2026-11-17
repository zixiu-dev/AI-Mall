#!/usr/bin/env python3
"""Offline H2 import. Stop the mall and initialize the MySQL schema before running."""
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import zipfile

root = Path(os.environ.get("MALL_PROJECT_DIR", Path(__file__).resolve().parents[1]))
connection = json.loads((root / ".runtime/mysql/connection.json").read_text())
report = root / ".runtime/mysql/h2-migration.tsv"
if report.exists():
    raise SystemExit("Migration report already exists; refusing to replace migrated data")
try:
    with socket.create_connection(("127.0.0.1", 8080), timeout=1):
        raise SystemExit("Stop the current mall on 8080 before migration")
except OSError:
    pass
backup = root / ".runtime/backups" / time.strftime("h2-to-mysql-%Y%m%d-%H%M%S")
backup.mkdir(parents=True)
os.chmod(backup, 0o700)
launch = root / ".runtime/order-demo/launch.json"
(backup / "launch.json").write_bytes(launch.read_bytes())
os.chmod(backup / "launch.json", 0o600)
source = next(x.split("=", 1)[1] for x in json.loads(launch.read_text())["applicationOptions"]
              if x.startswith("--spring.datasource.url=jdbc:h2:"))
jar = next((root / "target").glob("*.jar"))
libraries = root / ".runtime/mysql/migration-libs"
libraries.mkdir(exist_ok=True)
with zipfile.ZipFile(jar) as archive:
    for name in archive.namelist():
        if name.startswith(("BOOT-INF/lib/h2-", "BOOT-INF/lib/mysql-connector-j-")):
            (libraries / Path(name).name).write_bytes(archive.read(name))
values = {"source": source, "target": connection["url"], "username": connection["username"],
          "password": connection["password"], "backup": str(backup / "h2-database.zip")}
payload = "".join(key + "=" + value.replace("\\", "\\\\").replace("\n", "\\n") + "\n"
                  for key, value in values.items())
result = subprocess.run(["java", "-cp", str(libraries / "*"),
                         str(root / "scripts/MigrateH2ToMySQL.java")],
                        input=payload, text=True, capture_output=True)
if result.returncode:
    raise SystemExit("Migration failed and target transaction rolled back: " + result.stderr[-1800:])
if not result.stdout.rstrip().endswith("COMMITTED"):
    raise SystemExit("Migration did not return a commit receipt")
report.write_text(result.stdout)
os.chmod(report, 0o600)
print(result.stdout)
print("Backup directory:", backup)
