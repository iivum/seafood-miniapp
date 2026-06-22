#!/usr/bin/env bash
set -euo pipefail
REPO=$(git rev-parse --show-toplevel)
git config core.hooksPath .githooks
chmod +x "$REPO/.githooks/pre-commit"
echo "✅ git hooks 已安装 (core.hooksPath=.githooks)"
echo "   pre-commit: ./gradlew check -PexcludeTags=docker,native"
