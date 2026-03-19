#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ONTOUML_JS="${ONTOUML_JS:-$PROJECT_ROOT/../ontouml-js}"
RES_DIR="$PROJECT_ROOT/src/main/resources/ontouml2alloy"
ESBUILD_VERSION="0.28.0"

if [[ ! -d "$ONTOUML_JS" ]]; then
  echo "error: ontouml-js not found at $ONTOUML_JS" >&2
  exit 1
fi

mkdir -p "$RES_DIR"

pushd "$ONTOUML_JS" >/dev/null
echo ">> Building ontouml-js (npm ci && npm run build)"
npm ci --legacy-peer-deps
npm run build

echo ">> Bundling ontouml2alloy-cli with esbuild@$ESBUILD_VERSION"
npx -y "esbuild@$ESBUILD_VERSION" \
  dist/libs/ontouml2alloy/ontouml2alloy-cli.js \
  --bundle --platform=node --target=node18 --format=cjs \
  --outfile="$RES_DIR/ontouml2alloy-cli.bundle.js"

SHA="$(git rev-parse HEAD)"
DIRTY=""
if ! git diff --quiet || ! git diff --cached --quiet; then
  DIRTY="-dirty"
fi
popd >/dev/null

echo "Refreshed ontouml2alloy bundle from ontouml-js@${SHA}${DIRTY}"