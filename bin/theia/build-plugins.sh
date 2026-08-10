#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

# ============================================================================
# Packages every fzl-theia plugin project under src-projects/fzl-theia-plugins
# with @vscode/vsce, then installs it into src-projects/fzl-theia-plugins-dist
# — the directory fzl-theia's second THEIA_DEFAULT_PLUGINS local-dir source
# reads from (see docker-compose.yml and containers/fzl-theia/Dockerfile).
#
# "Installs" means unpacked, not the .vsix file itself. Theia only applies its
# .vsix file handler to *user* plugins (the ones installed through the UI); a
# .vsix sitting in a local-dir system plugin source is skipped with
#
#   PluginDeployerImpl WARN Only user plugins will be handled by file
#   handlers, please unpack the plugin '<name>.vsix' manually.
#
# so each plugin is unzipped here into DIST_DIR/<name>/ (the archive's
# extension/ folder, which is what holds package.json). The .vsix itself is
# left next to the plugin sources, for distribution or a manual UI install.
#
# Restart the container afterward to pick up new/updated plugins — no image
# rebuild needed:
#   docker compose restart fzl-theia
#
# Each subdirectory of fzl-theia-plugins is expected to be a standalone VS
# Code plugin project (its own package.json with a "publisher", "engines.vscode"
# and "contributes"). Run this on the host (needs Node/npm/unzip) or from
# inside the fzl-theia container's own integrated terminal.
# ============================================================================

log() { echo "[build-plugins] $*"; }

if ! command -v unzip >/dev/null 2>&1; then
    log "unzip is required to unpack the .vsix — install it (dnf install unzip) and re-run."
    exit 1
fi

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
    vsix="${plugin_dir}${name}.vsix"
    (
        cd "$plugin_dir"
        npm install --silent
        # --no-dependencies: the plugins here bundle their runtime deps (esbuild
        # or similar), so node_modules is not shipped inside the archive.
        npx --yes @vscode/vsce package --no-dependencies --out "$vsix"
    )

    log "Installing $name into the fzl-theia plugin directory..."
    rm -rf "${DIST_DIR:?}/$name" "$DIST_DIR/.unpack-$name"
    unzip -q "$vsix" -d "$DIST_DIR/.unpack-$name"
    mv "$DIST_DIR/.unpack-$name/extension" "$DIST_DIR/$name"
    rm -rf "$DIST_DIR/.unpack-$name"

    log "-> src-projects/fzl-theia-plugins-dist/${name}/ (archive: ${vsix#"$REPO_ROOT"/})"
done

log "Done. Restart fzl-theia to load new/updated plugins: docker compose restart fzl-theia"
