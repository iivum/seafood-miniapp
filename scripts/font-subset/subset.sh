#!/usr/bin/env bash
#
# OD v2 字体子集化复现脚本(路线图 task 1.16 + 1.17)。
#
# 输入:GitHub google/fonts + notofonts/noto-cjk 的 4 个原 TTF/OTF
# 输出:frontend/assets/fonts/{4 个 woff2} + glyphs.txt 字频表
#
# 跑法(从仓库根):
#   pip3 install fonttools zopfli
#   ./scripts/font-subset/subset.sh
#
# 注:本脚本是 Sprint 0 spike 产物;Sprint 1 末若包大小 > 20% 超预算,触发 MVP 降级
# (设计 § 决策 3)— 删 Fraunces,衬线 display 用 system serif 替代。届时改本脚本
# 跳过 Fraunces 子集,只产 3 个 woff2。

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRATCH_DIR="${SCRATCH_DIR:-/tmp/seafood-font-subset}"
ASSETS_DIR="$REPO_ROOT/frontend/assets/fonts"
GLYPHS_TXT="$REPO_ROOT/scripts/font-subset/glyphs.txt"

# 字频表去重
GLYPHS_OUT="$SCRATCH_DIR/glyphs.txt"
mkdir -p "$SCRATCH_DIR"
python3 -c "
with open('$GLYPHS_TXT', encoding='utf-8') as f:
    text = f.read()
seen = set(); uniq = []
for c in text:
    if c.isspace() or c in seen:
        continue
    seen.add(c); uniq.append(c)
with open('$GLYPHS_OUT', 'w', encoding='utf-8') as f:
    f.write(''.join(uniq))
print(f'[subset] glyphs unique: {len(uniq)}')
"

# 拉源字体(若缓存)
declare -A SOURCES=(
  ["Fraunces.ttf"]="https://github.com/google/fonts/raw/main/ofl/fraunces/Fraunces%5BSOFT%2CWONK%2Copsz%2Cwght%5D.ttf"
  ["InterTight.ttf"]="https://github.com/google/fonts/raw/main/ofl/intertight/InterTight%5Bwght%5D.ttf"
  ["GeistMono.ttf"]="https://github.com/google/fonts/raw/main/ofl/geistmono/GeistMono%5Bwght%5D.ttf"
  ["NotoSansSC.otf"]="https://github.com/notofonts/noto-cjk/raw/main/Sans/SubsetOTF/SC/NotoSansSC-Regular.otf"
)

for f in "${!SOURCES[@]}"; do
  if [ ! -f "$SCRATCH_DIR/$f" ]; then
    echo "[subset] downloading $f"
    curl -sL -o "$SCRATCH_DIR/$f" "${SOURCES[$f]}"
  fi
done

# subset 配置(统一)— 决策 3 路线图 1.16/1.17
SUBSET_FLAGS=(
  --text-file="$GLYPHS_OUT"
  --flavor=woff2
  --no-hinting
  --desubroutinize
  --name-IDs='*'
  --name-legacy
  --name-languages='*'
  --layout-features='*'
  --notdef-glyph --notdef-outline
  --recommended-glyphs
  --drop-tables=DSIG
)

declare -A JOBS=(
  ["Fraunces.ttf"]="fraunces-subset.woff2"
  ["InterTight.ttf"]="inter-tight-subset.woff2"
  ["GeistMono.ttf"]="geist-mono-subset.woff2"
  ["NotoSansSC.otf"]="noto-sans-sc-subset.woff2"
)

mkdir -p "$ASSETS_DIR"
for src in "${!JOBS[@]}"; do
  out="${JOBS[$src]}"
  echo "[subset] $src -> $out"
  python3 -m fontTools.subset "$SCRATCH_DIR/$src" "${SUBSET_FLAGS[@]}" \
    --output-file="$SCRATCH_DIR/$out"
  cp "$SCRATCH_DIR/$out" "$ASSETS_DIR/$out"
done

cp "$GLYPHS_OUT" "$ASSETS_DIR/glyphs.txt"

# 摘要
echo
echo "=== subset 落地完成 ==="
du -h "$ASSETS_DIR"/*.woff2
total=$(du -cb "$ASSETS_DIR"/*.woff2 | tail -1 | awk '{print $1}')
echo "TOTAL: $((total / 1024))KB / 200KB budget (Sprint 0 末 spike 重审)"
