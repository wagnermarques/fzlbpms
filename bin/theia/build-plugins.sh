#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

# ============================================================================
# Packages every fzl-theia plugin project under src-projects/fzl-theia-plugins
# into a .vsix and drops it into src-projects/fzl-theia-plugins-dist — the
# directory fzl-theia's second THEIA_DEFAULT_PLUGINS local-dir source reads
# from (see docker-compose.yml and containers/fzl-theia/Dockerfile).
#
# Restart the container afterward to pick up new/updated plugins — no image
# rebuild needed:
#   docker compose restart fzl-theia
#
# Each subdirectory of fzl-theia-plugins is expected to be a standalone VS
# Code / Theia plugin project (its own package.json), packaged here with
# @vscode/vsce. Run this on the host (needs Node/npm) or from inside the
# fzl-theia container's own integrated terminal, which already has both.
# ============================================================================

log() { echo "[build-plugins] $*"; }

REPO_ROOT="$(pwd)"
SRC_DIR="$REPO_ROOT/src-projects/fzl-theia-plugins"
DIST_DIR="$REPO_ROOT/src-projects/fzl-theia-plugins-dist"
mkdir -p "$DIST_DIR"

shopt -s nullglob
plugin_dirs=("$SRC_DIR"/*/)
shopt -u nullglob

if [ ${#plugin_dirs[@]} -eq 0 ]; then
    log "No plugin projects found under src-projects/fzl-theia-plugins yet — nothing to build."
    exit 0
fi

for plugin_dir in "${plugin_dirs[@]}"; do
    name=$(basename "$plugin_dir")

    if [ ! -f "${plugin_dir}package.json" ]; then
        log "Skipping $name (no package.json)"
        continue
    fi

    log "Building $name..."
    (
        cd "$plugin_dir"
        npm install --silent
        npx --yes @vscode/vsce package --no-dependencies --out "$DIST_DIR/${name}.vsix"
    )
    log "-> src-projects/fzl-theia-plugins-dist/${name}.vsix"
done

log "Done. Restart fzl-theia to load new/updated plugins: docker compose restart fzl-theia"
