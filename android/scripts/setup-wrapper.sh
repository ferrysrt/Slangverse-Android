#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if ! command -v gradle >/dev/null 2>&1; then
  echo 'Gradle CLI is not installed. Install compatible Gradle or use Android Studio with Gradle support.' >&2
  echo 'This project intentionally does not contain an unverified wrapper JAR.' >&2
  exit 1
fi
gradle wrapper --gradle-version 8.14.3 --distribution-type bin
printf 'Wrapper generated. Next run: ./gradlew :app:assembleDebug\n'
