#!/usr/bin/env bash
set -euo pipefail

task_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
task_accept=0
task_warmup=1
for task_arg in "$@"; do
  case "$task_arg" in
    --accept-licenses) task_accept=1 ;;
    --skip-warmup) task_warmup=0 ;;
    *) printf 'Unknown option: %s\n' "$task_arg" >&2; exit 2 ;;
  esac
done
[[ "$(uname -s)" == Linux && "$(uname -m)" == x86_64 ]] || {
  echo 'This bootstrap supports Linux x86_64. On Windows use scripts/check-android.ps1.' >&2
  exit 2
}
for task_command in curl unzip python3; do
  command -v "$task_command" >/dev/null || { echo "Missing prerequisite: $task_command" >&2; exit 2; }
done

# Prefer Java 17 even when the host default is a newer, incompatible JDK.
for task_java in "${JAVA_HOME_17_X64:-}" /usr/lib/jvm/java-17-openjdk-amd64 "${JAVA_HOME:-}"; do
  if [[ -n "$task_java" && -x "$task_java/bin/java" ]]; then
    task_major="$("$task_java/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
    if [[ "$task_major" == 17 ]]; then export JAVA_HOME="$task_java"; break; fi
  fi
done
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  echo 'Install JDK 17 and set JAVA_HOME before running setup.' >&2; exit 2
fi
task_major="$("$JAVA_HOME/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
[[ "$task_major" == 17 ]] || { echo 'JDK 17 is required by this reproducible setup.' >&2; exit 2; }
export PATH="$JAVA_HOME/bin:$PATH"

task_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/.local/share/turnisync/android-sdk}}"
mkdir -p "$task_sdk"
task_sdk="$(cd "$task_sdk" && pwd)"
export ANDROID_HOME="$task_sdk" ANDROID_SDK_ROOT="$task_sdk"
task_manager="$task_sdk/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$task_manager" ]]; then
  task_existing="$(command -v sdkmanager || true)"
  if [[ -n "$task_existing" ]]; then
    task_manager="$task_existing"
  else
    [[ "$task_accept" == 1 ]] || {
      echo 'Android SDK tools are missing. Read the SDK license, then rerun with --accept-licenses.' >&2; exit 2
    }
    task_download="$(mktemp -d -t turnisync-sdk.XXXXXXXX)"
    trap 'rm -rf -- "$task_download"' EXIT
    curl --fail --location --retry 3 --output "$task_download/tools.zip" \
      https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
    printf '%s  %s\n' 4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583 "$task_download/tools.zip" | sha256sum -c -
    unzip -q "$task_download/tools.zip" -d "$task_download/extracted"
    [[ ! -e "$task_sdk/cmdline-tools/latest" ]] || {
      echo 'An incomplete cmdline-tools/latest already exists; repair it rather than overwriting it.' >&2; exit 2
    }
    mkdir -p "$task_sdk/cmdline-tools"
    mv "$task_download/extracted/cmdline-tools" "$task_sdk/cmdline-tools/latest"
  fi
fi

if [[ "$task_accept" == 1 ]]; then
  # yes may receive SIGPIPE after sdkmanager exits; retain sdkmanager's status.
  set +o pipefail
  yes | "$task_manager" --sdk_root="$task_sdk" --licenses
  task_license_status=${PIPESTATUS[1]}
  set -o pipefail
  [[ "$task_license_status" == 0 ]] || exit "$task_license_status"
fi
"$task_manager" --sdk_root="$task_sdk" --install 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0'

export PATH="$task_sdk/platform-tools:$PATH"
TASK_SDK_PATH="$task_sdk" TASK_REPO_ROOT="$task_root" python3 - <<'PY'
import os
from pathlib import Path
p = Path(os.environ['TASK_REPO_ROOT']) / 'android-app/local.properties'
value = os.environ['TASK_SDK_PATH'].replace('\\', '\\\\').replace(':', '\\:').replace(' ', '\\ ')
p.write_text('sdk.dir=' + value + '\n', encoding='utf-8')
PY
{
  printf 'export JAVA_HOME=%q\n' "$JAVA_HOME"
  printf 'export ANDROID_HOME=%q\n' "$task_sdk"
  printf 'export ANDROID_SDK_ROOT=%q\n' "$task_sdk"
  printf 'export PATH=%q:"$PATH"\n' "$JAVA_HOME/bin:$task_sdk/platform-tools"
} > "$task_root/.turnisync-cloud.env"

echo 'Java 17 and Android SDK 36 configured. Local machine paths remain untracked.'
if [[ "$task_warmup" == 1 ]]; then bash "$task_root/scripts/check-android.sh"; fi
