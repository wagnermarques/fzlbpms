#!/usr/bin/env bash
# =============================================================================
# switch-env.sh — Switch fzlbpms between development and production profiles.
#
# Usage:
#   ./bin/switch-env.sh dev    # Activates .env.dev and points to fzlbpms.local
#   ./bin/switch-env.sh prod   # Activates .env.prod and points to fzlbpms.com.br
# =============================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

TARGET="${1:-dev}"

log() { echo "[switch-env] $*"; }

case "$TARGET" in
    dev|development|local)
        if [ ! -f ".env.dev" ]; then
            log "Creating .env.dev from .env..."
            cp .env .env.dev
        fi
        log "Applying development profile (.env.dev -> .env)..."
        cp .env.dev .env
        ./bin/switch-domain.sh fzlbpms.local
        log "Successfully switched to DEVELOPMENT (fzlbpms.local)"
        ;;
    prod|production|server)
        if [ ! -f ".env.prod" ]; then
            log "Creating .env.prod from .env..."
            cp .env .env.prod
        fi
        log "Applying production profile (.env.prod -> .env)..."
        cp .env.prod .env
        ./bin/switch-domain.sh fzlbpms.com.br
        log "Successfully switched to PRODUCTION (fzlbpms.com.br)"
        ;;
    -h|--help|help)
        echo "Usage: $0 [dev|prod]"
        echo ""
        echo "Switch fzlbpms environment profile and configure domain settings."
        echo ""
        echo "Options:"
        echo "  dev, local         Switch to development profile (.env.dev -> fzlbpms.local)"
        echo "  prod, production   Switch to production profile (.env.prod -> fzlbpms.com.br)"
        echo "  -h, --help         Show this help message"
        exit 0
        ;;
    *)
        echo "Usage: $0 [dev|prod]" >&2
        echo "Run '$0 --help' for details." >&2
        exit 1
        ;;
esac
