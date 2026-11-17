#!/usr/bin/env python3
"""Start the project-owned MySQL instance with local-only networking."""
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import time

root = Path(os.environ.get("MALL_PROJECT_DIR", Path(__file__).resolve().parents[1]))
folder = root / ".runtime/mysql"
folder.mkdir(parents=True, exist_ok=True)
os.chmod(folder, 0o700)
binary = Path(os.environ.get("MYSQL_HOME", "/usr/local/mysql")) / "bin"
config = folder / "my.cnf"
connection_file = folder / "connection.json"
socket_file = folder / "mysql.sock"

def private_write(path, content):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as stream:
        stream.write(content)
    os.chmod(path, 0o600)

def available():
    try:
        with socket.create_connection(("127.0.0.1", 3307), timeout=1):
            return True
    except OSError:
        return False

def wait_for_mysql(process):
    for _ in range(120):
        probe = subprocess.run([str(binary / "mysqladmin"), "--no-defaults",
                                "--socket=" + str(socket_file), "--user=root", "ping"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if probe.returncode == 0:
            return
        if process.poll() is not None:
            raise SystemExit("MySQL exited. Check .runtime/mysql/mysql-error.log")
        time.sleep(0.5)
    raise SystemExit("MySQL startup timed out")

def start():
    with (folder / "console.log").open("ab") as log:
        process = subprocess.Popen([str(binary / "mysqld"), "--defaults-file=" + str(config)],
                                   stdin=subprocess.DEVNULL, stdout=log,
                                   stderr=subprocess.STDOUT, start_new_session=True)
    wait_for_mysql(process)
    return process

if available():
    if connection_file.exists():
        probe = subprocess.run([str(binary / "mysql"), "--defaults-extra-file=" +
                                str(folder / "client.cnf"), "--execute=SELECT 1"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if probe.returncode == 0:
            print("Project MySQL is ready on 127.0.0.1:3307")
            raise SystemExit(0)
    raise SystemExit("Port 3307 is already in use by another instance")

initial = not (folder / "data/mysql").exists()
base_config = f"""[mysqld]
basedir={binary.parent}
datadir={folder}/data
socket={socket_file}
pid-file={folder}/mysql.pid
log-error={folder}/mysql-error.log
bind-address=127.0.0.1
port=3307
mysqlx=0
skip-name-resolve
character-set-server=utf8mb4
collation-server=utf8mb4_bin
default-time-zone=+08:00
innodb-buffer-pool-size=256M
max-connections=150
"""
if initial:
    data = folder / "data"
    data.mkdir(exist_ok=True)
    with (folder / "initialize.log").open("ab") as log:
        subprocess.run([str(binary / "mysqld"), "--no-defaults", "--initialize-insecure",
                        "--basedir=" + str(binary.parent), "--datadir=" + str(data)],
                       stdout=log, stderr=subprocess.STDOUT, check=True)
    # Provision through a Unix socket before enabling TCP.
    private_write(config, base_config + "skip-networking=ON\n")
    process = start()
    root_password = secrets.token_hex(32)
    app_password = secrets.token_hex(32)
    sql = f"""ALTER USER 'root'@'localhost' IDENTIFIED BY '{root_password}';
CREATE DATABASE bit_mall CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
CREATE DATABASE bit_mall_mysql_test CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
CREATE USER 'bitmall'@'127.0.0.1' IDENTIFIED BY '{app_password}';
GRANT ALL PRIVILEGES ON bit_mall.* TO 'bitmall'@'127.0.0.1';
GRANT ALL PRIVILEGES ON bit_mall_mysql_test.* TO 'bitmall'@'127.0.0.1';
"""
    subprocess.run([str(binary / "mysql"), "--no-defaults", "--socket=" + str(socket_file),
                    "--user=root"], input=sql, text=True, check=True, capture_output=True)
    private_write(folder / "root.cnf",
                  f"[client]\nuser=root\npassword={root_password}\nsocket={socket_file}\n")
    private_write(folder / "client.cnf",
                  f"[client]\nuser=bitmall\npassword={app_password}\nhost=127.0.0.1\nport=3307\nprotocol=TCP\n")
    private_write(connection_file, json.dumps({
        "url": "jdbc:mysql://127.0.0.1:3307/bit_mall?characterEncoding=UTF-8&connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true",
        "username": "bitmall", "password": app_password
    }, indent=2) + "\n")
    subprocess.run([str(binary / "mysqladmin"), "--defaults-extra-file=" +
                    str(folder / "root.cnf"), "shutdown"], check=True, capture_output=True)
    process.wait(timeout=30)
elif not connection_file.exists():
    raise SystemExit("Existing data directory has no connection metadata; check provisioning")

private_write(config, base_config)
process = start()
print("Project MySQL started on 127.0.0.1:3307, PID", process.pid)
