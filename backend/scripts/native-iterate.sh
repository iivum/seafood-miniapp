#!/usr/bin/env bash
# Helper: run nativeCompile, harvest missing --initialize-at-build-time classes,
# append them to build.gradle. Re-run until success or no new classes.
set -e

cd "$(dirname "$0")/.."
export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home

for i in 1 2 3 4 5 6 7 8 9 10; do
  echo "=== attempt $i ==="
  if ./gradlew --no-daemon nativeCompile 2>&1 | tee /tmp/native.log; then
    echo "=== native compile succeeded ==="
    ls -lh build/native/nativeCompile/seafood-backend
    exit 0
  fi
  NEW=$(grep "'\-\-initialize-at-build-time=" /tmp/native.log | sort -u || true)
  if [ -z "$NEW" ]; then
    echo "=== failed without init hints — inspect /tmp/native.log ==="
    tail -40 /tmp/native.log
    exit 1
  fi
  echo "=== adding init classes: ==="
  echo "$NEW"
  # extract just the fully-qualified class names
  CLASSES=$(echo "$NEW" | sed -E "s/.*'--initialize-at-build-time=([^']+)'.*/\1/" | sort -u)
  for c in $CLASSES; do
    # split on dots to take top 2 packages for glob
    PKG=$(echo "$c" | cut -d. -f1-2)
    if ! grep -q "'--initialize-at-build-time=$PKG'" build.gradle; then
      sed -i '' "s|'--initialize-at-build-time=java.util.TimeZone',|                '--initialize-at-build-time=$PKG',\\n                '--initialize-at-build-time=java.util.TimeZone',|" build.gradle
      echo "  added $PKG"
    fi
  done
done
echo "=== too many iterations ==="
exit 1
