#!/usr/bin/env bash
# check-no-refresh-scope.sh
# 禁止任何 Java 文件引用 @RefreshScope —— 该注解不兼容 GraalVM Native Image
# (参见 design.md §3.3 已知 Native 模式陷阱,§10 风险 #7)
# 退出码: 0 通过 / 1 检出违规

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/src"

if [ ! -d "$SRC" ]; then
  echo "[check] no src/ directory under $ROOT — nothing to scan"
  exit 0
fi

# 匹配 @RefreshScope 出现,排除空 import / 注释
# -E: 扩展正则  -R: 递归  -n: 行号
HITS=$(grep -REn --include='*.java' \
  -e '[[:space:]]*\*.*RefreshScope' \
  -e '^\s*@RefreshScope' \
  -e '\bimport\b.*\.RefreshScope' \
  "$SRC" || true)

if [ -n "$HITS" ]; then
  echo "[check] FAIL — @RefreshScope is forbidden (incompatible with GraalVM Native Image):"
  echo "$HITS"
  exit 1
fi

echo "[check] OK — no @RefreshScope references in $SRC"
exit 0
