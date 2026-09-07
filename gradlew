#!/bin/sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed. Install Gradle 9.6+ or open this project in Android Studio and use its Gradle tooling." >&2
exit 1
