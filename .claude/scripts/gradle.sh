#!/usr/bin/env bash
# Runs Gradle inside the Claude sandbox VM. On Windows use ./gradlew instead.
#
#   .claude/scripts/gradle.sh <gradle args...>
#   .claude/scripts/gradle.sh --save-cache      push downloaded deps back to ai/gradle-home
#
# Three things this handles that ./gradlew cannot in the sandbox: no JDK 25, no Gradle, and a
# network reachable only through an authenticating proxy Java does not read from the environment.
# It also keeps the dependency cache and build outputs off the Windows mount, which writes at
# ~12 MB/s against ~2 GB/s for the VM's own disk - worth 7.2s -> 2.7s on a warm build.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLCHAIN="$ROOT/ai/toolchain"          # on the mount: survives between sessions
LOCAL="$HOME/.pwt"                      # VM-local: fast, wiped when the sandbox goes away
SEED="$ROOT/ai/gradle-home"             # durable copy of the dependency cache
GRADLE_VERSION="9.7.1"
JDK_DIR="$TOOLCHAIN/jdk-25"
GRADLE_DIR="$TOOLCHAIN/gradle-$GRADLE_VERSION"
JDK_URL="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz"

mkdir -p "$TOOLCHAIN" "$LOCAL"

if [[ "${1:-}" == "--save-cache" ]]; then
  echo "[cache] copying newly downloaded dependencies back to ai/gradle-home (~11s) ..." >&2
  mkdir -p "$SEED"
  cp -rn "$LOCAL/gradle-home/." "$SEED/" 2>/dev/null || true
  echo "[cache] done" >&2
  exit 0
fi

if [[ ! -x "$JDK_DIR/bin/javac" ]]; then
  echo "[bootstrap] downloading Temurin 25 ..." >&2
  curl -sSL --max-time 600 -o "$LOCAL/jdk.tar.gz" "$JDK_URL"
  mkdir -p "$JDK_DIR"
  tar xzf "$LOCAL/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
  rm -f "$LOCAL/jdk.tar.gz"
fi

if [[ ! -x "$GRADLE_DIR/bin/gradle" ]]; then
  echo "[bootstrap] downloading Gradle $GRADLE_VERSION ..." >&2
  curl -sSL --max-time 600 -o "$LOCAL/gradle.zip" \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  unzip -q "$LOCAL/gradle.zip" -d "$TOOLCHAIN"
  rm -f "$LOCAL/gradle.zip"
fi

# Seed the fast local cache from the durable one, once per sandbox (~25s).
if [[ ! -d "$LOCAL/gradle-home" && -d "$SEED" ]]; then
  echo "[cache] seeding local dependency cache from ai/gradle-home (~25s, once per session) ..." >&2
  cp -r "$SEED" "$LOCAL/gradle-home"
fi

# Build outputs go to the VM's disk, so they never touch the Windows mount and never fight
# IntelliJ over <project>/build. The trailing "/build" matters: ArchUnit's DoNotIncludeTests
# recognises test output by the ".../build/classes/java/test/" path shape, and silently analyses
# test classes as production code without it.
cat > "$LOCAL/local-build.gradle" <<'INIT'
allprojects {
  layout.buildDirectory = file("${System.getenv('HOME')}/.pwt/out/${project.path.replace(':', '_')}/build")
}
INIT

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$LOCAL/gradle-home}"

# Java ignores HTTPS_PROXY. The credentials are regenerated per shell invocation, so they are
# read at runtime and never stored.
PROXY_ARGS=()
if [[ -n "${HTTPS_PROXY:-}" ]]; then
  eval "$(python3 - <<'PY'
import os, urllib.parse, shlex
p = urllib.parse.urlparse(os.environ['HTTPS_PROXY'])
u = urllib.parse.unquote(p.username or '')
w = urllib.parse.unquote(p.password or '')
print("PX_HOST=%s PX_PORT=%s PX_USER=%s PX_PASS=%s" % (p.hostname, p.port, shlex.quote(u), shlex.quote(w)))
PY
)"
  for s in http https; do
    PROXY_ARGS+=("-D${s}.proxyHost=$PX_HOST" "-D${s}.proxyPort=$PX_PORT" \
                 "-D${s}.proxyUser=$PX_USER" "-D${s}.proxyPassword=$PX_PASS")
  done
  PROXY_ARGS+=("-Dhttp.nonProxyHosts=localhost|127.0.0.1" \
               "-Djdk.http.auth.tunneling.disabledSchemes=" \
               "-Djdk.http.auth.proxying.disabledSchemes=")
fi

# 2 cores and ~4 GB here; the committed gradle.properties is tuned for a real desktop.
exec "$GRADLE_DIR/bin/gradle" -p "$ROOT" -I "$LOCAL/local-build.gradle" \
  --console=plain --no-parallel --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xms256m -Xmx1g -Dfile.encoding=UTF-8" \
  "${PROXY_ARGS[@]}" "$@"
