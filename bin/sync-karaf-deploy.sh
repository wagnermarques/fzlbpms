#!/usr/bin/env bash
# ==============================================================================
# sync-karaf-deploy.sh — Sincroniza Blueprint XMLs de origem para karaf_deploy_dir
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

SRC_DIR="${ROOT_DIR}/src-projects/karaf_bundles/blueprint-xmls-bundles"
DEPLOY_DIR="${ROOT_DIR}/src-projects/karaf_deploy_dir"

echo "=== Sincronizando Blueprint XMLs para deploy no Karaf ==="
echo "Origem : ${SRC_DIR}"
echo "Destino: ${DEPLOY_DIR}"
echo ""

if [[ ! -d "${SRC_DIR}" ]]; then
    echo "Erro: Diretório de origem não encontrado: ${SRC_DIR}" >&2
    exit 1
fi

if [[ ! -d "${DEPLOY_DIR}" ]]; then
    echo "Erro: Diretório de deploy não encontrado: ${DEPLOY_DIR}" >&2
    exit 1
fi

shopt -s nullglob
xml_files=("${SRC_DIR}"/*.xml)
shopt -u nullglob

if [[ ${#xml_files[@]} -eq 0 ]]; then
    echo "Nenhum arquivo .xml encontrado em ${SRC_DIR}."
    exit 0
fi

for src_file in "${xml_files[@]}"; do
    filename="$(basename "${src_file}")"
    dest_file="${DEPLOY_DIR}/${filename}"

    if [[ -f "${dest_file}" ]]; then
        src_hash=$(sha256sum "${src_file}" | awk '{print $1}')
        dest_hash=$(sha256sum "${dest_file}" | awk '{print $1}')

        if [[ "${src_hash}" == "${dest_hash}" ]]; then
            echo "[OK - Idêntico] ${filename}"
            continue
        else
            echo "[ATUALIZANDO]   ${filename} (hashes diferentes)"
        fi
    else
        echo "[NOVO DEPLOY]   ${filename}"
    fi

    cp -p "${src_file}" "${dest_file}"
    chmod 664 "${dest_file}" 2>/dev/null || true
    echo "                -> Copiado com sucesso para deploy."
done

echo ""
echo "=== Sincronização concluída com sucesso ==="
