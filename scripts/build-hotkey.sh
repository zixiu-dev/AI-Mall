#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
source_dir="$project_dir/.runtime/hotkey-source"
revision="f1c81819c7dcc7cf3421f556698e0fbebd244641"
mkdir -p "$project_dir/.runtime"
if [[ ! -d "$source_dir/.git" ]]; then
  git init "$source_dir"
  git -C "$source_dir" remote add origin https://gitee.com/jd-platform-opensource/hotkey.git
  git -C "$source_dir" fetch --depth 1 origin "$revision"
  git -C "$source_dir" checkout --detach FETCH_HEAD
fi
[[ "$(git -C "$source_dir" rev-parse HEAD)" == "$revision" ]] || {
  echo "HotKey source revision differs from the pinned revision; check $source_dir" >&2; exit 1;
}
# The only upstream source adaptation removes unused JDK-internal memory diagnostics.
if git -C "$source_dir" apply --check "$project_dir/deploy/hotkey/java17-worker.patch" 2>/dev/null; then
  git -C "$source_dir" apply "$project_dir/deploy/hotkey/java17-worker.patch"
else
  git -C "$source_dir" apply --reverse --check "$project_dir/deploy/hotkey/java17-worker.patch"
fi
mvn -f "$source_dir/pom.xml" -pl common,client -am install -DskipTests -Dassembly.skipAssembly=true \
  -Dmaven.compiler.release=8 -Dmaven.compiler.source=8 -Dmaven.compiler.target=8 -q
mvn -f "$source_dir/worker/pom.xml" package -DskipTests \
  -Dmaven.compiler.release=8 -Dmaven.compiler.source=8 -Dmaven.compiler.target=8 -q
cp "$source_dir/worker/target/worker-0.0.4-SNAPSHOT.jar" "$project_dir/.runtime/hotkey-worker.jar"
echo "JD HotKey official client installed; Worker ready: .runtime/hotkey-worker.jar"
