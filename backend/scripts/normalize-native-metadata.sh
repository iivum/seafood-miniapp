#!/usr/bin/env bash
# normalize-native-metadata.sh
# ============================================================
# 用途:Sprint 2 C5 §5.3 — 把 nativeTest agent 产出的
# `build/native/agent-output/test/*.json` 归一化(排序 key、去重条目)后
# 拷贝到 `src/main/resources/META-INF/native-image/`,并对比 git HEAD 的
# committed 版本,如果存在非空 diff 则退出非 0(让 CI 拦截"忘了提交新 metadata"
# 的 PR)。
#
# 依赖:`jq`(macOS `brew install jq` / apt `apt-get install jq`)、`git`、`bash 4+`。
#
# 用法:
#   bash scripts/normalize-native-metadata.sh
#
# 退出码:
#   0  = 本次归一化产物与 HEAD committed 版本一致(可能都是空 [] / {})
#   0  = 与 HEAD 不一致 — 仅 ::warning:: annotation,Pipeline 继续推进;
#        提交方需把 src/main/resources/META-INF/native-image/ 下的更新加入 commit
#        (drift 设计如此:agent 输出非 byte-stable,Sprint 2 C5 决定不再硬拦截)
#   2  = 必要依赖缺失 / 输入文件缺失 / 致命错误
# ============================================================

set -euo pipefail

# ---- 路径解析(脚本位于 backend/scripts/,工作目录随意) ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_META="$BACKEND_DIR/src/main/resources/META-INF/native-image"
AGENT_OUT="$BACKEND_DIR/build/native/agent-output/test"

# ---- 工具检查 ----
for bin in jq git; do
  if ! command -v "$bin" >/dev/null 2>&1; then
    echo "[normalize] FAIL — required tool '$bin' not found in PATH" >&2
    exit 2
  fi
done

if [ ! -d "$AGENT_OUT" ]; then
  echo "[normalize] FAIL — agent output dir missing: $AGENT_OUT" >&2
  echo "  (run './gradlew nativeTest' first; the agent writes here)" >&2
  exit 2
fi

mkdir -p "$SRC_META"

# ---- 归一化并拷贝:对 agent 产出的每个 *.json ----
shopt -s nullglob
for src in "$AGENT_OUT"/*.json; do
  name="$(basename "$src")"
  dest="$SRC_META/$name"
  case "$(jq -r 'type' "$src" 2>/dev/null)" in
    array)
      # 数组:sort + unique
      jq -s 'unique | sort' "$src" > "$dest" || {
        echo "[normalize] FAIL — jq failed on $src" >&2; exit 2;
      }
      ;;
    object)
      # 对象:key 排序,内嵌数组递归排序去重
      jq 'walk(if type == "array" then sort else . end)' "$src" > "$dest" || {
        echo "[normalize] FAIL — jq failed on $src" >&2; exit 2;
      }
      # 对象数组额外去重(同 name 的多条目只留一条)
      jq 'def dedup_obj_array: if (.[0] | type) == "object" then unique_by(.name // .className // .id // .) else . end;
          walk(if type == "array" then dedup_obj_array else . end)' \
        "$dest" > "$dest.tmp" && mv "$dest.tmp" "$dest"
      ;;
    *)
      echo "[normalize] FAIL — unexpected JSON root type in $src" >&2; exit 2;
      ;;
  esac
done

# ---- 对比 HEAD ----
cd "$BACKEND_DIR"
if ! git rev-parse --git-dir >/dev/null 2>&1; then
  echo "[normalize] FAIL — $BACKEND_DIR is not inside a git repo" >&2
  exit 2
fi

if git diff --quiet -- \
    "src/main/resources/META-INF/native-image/" \
    && [ -z "$(git ls-files --others --exclude-standard src/main/resources/META-INF/native-image/ 2>/dev/null)" ]; then
  echo "[normalize] OK — META-INF/native-image/ matches HEAD"
  exit 0
fi

echo "[normalize] DRIFT — META-INF/native-image/ differs from HEAD:"
git --no-pager diff -- \
  "src/main/resources/META-INF/native-image/" || true
echo
echo "[normalize] Untracked files in META-INF/native-image/:"
git ls-files --others --exclude-standard src/main/resources/META-INF/native-image/ || true
echo
# Sprint 2 CI 修复 (2026-06-08):drift 分支从 exit 1 改为 ::warning:: + exit 0。
# 原 exit 1 被 workflow 的 continue-on-error: true 吞掉不 fail run,但 GitHub UI
# 在 step detail 仍显示 "Error: Process completed with exit code 1" 干扰阅读。
# 改 ::warning:: 标 annotation,drift 仍输出到 log 供人 review,但 step 标 green。
# 退出码契约见文件头注释。
echo "[normalize] ACTION: review the diff above, commit the regenerated" >&2
echo "              JSON, and re-run the native pipeline." >&2
echo "::warning::normalize-native-metadata drift detected, see log above"
exit 0
