#!/usr/bin/env python3
"""
Publishes/seeds Orbeon Form definitions into the PostgreSQL `orbeon` database
for both Form Runner and Form Builder.
"""

import os
import re
import subprocess

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

FORMS = [
    {
        "dir": "doc_13_1_parecer_comissao",
        "app": "etec-coord",
        "form": "parecer-comissao",
        "doc_id": "fb-parecer-comissao",
        "title": "Doc 13.1 - Anexo I: Parecer da Comissão de Professores",
        "description": "Formulário de Análise Documental e Parecer da Comissão Especial de Professores para Aproveitamento de Estudos.",
    },
    {
        "dir": "doc_13_2_despacho_diretor",
        "app": "etec-coord",
        "form": "despacho-diretor",
        "doc_id": "fb-despacho-diretor",
        "title": "Doc 13.2 - Anexo II: Despacho do Diretor de Escola Técnica",
        "description": "Formulário de Despacho e Decisão Final da Direção sobre o Processo de Aproveitamento de Estudos.",
    },
    {
        "dir": "doc_13_processo_aproveitamento_unificado",
        "app": "etec-coord",
        "form": "processo-aproveitamento",
        "doc_id": "fb-processo-aproveitamento",
        "title": "Processo de Aproveitamento de Estudos (Docs 13.1 e 13.2)",
        "description": "Emissão simultânea do Parecer da Comissão e Despacho do Diretor para Aproveitamento de Estudos.",
    },
]


def extract_metadata_xml(xhtml_content: str, app: str, form: str, title: str, description: str) -> str:
    """Extract <metadata>...</metadata> from form.xhtml or construct it."""
    match = re.search(r"<metadata>[\s\S]*?</metadata>", xhtml_content)
    if match:
        meta = match.group(0)
        if "<available>" not in meta:
            meta = meta.replace("</metadata>", "    <available>true</available>\n</metadata>")
        return meta

    return f"""<metadata>
    <application-name>{app}</application-name>
    <form-name>{form}</form-name>
    <title xml:lang="pt">{title}</title>
    <description xml:lang="pt">{description}</description>
    <version-comment>1.0.0</version-comment>
    <available>true</available>
</metadata>"""


def publish_form(form_info):
    form_dir = os.path.join(BASE_DIR, form_info["dir"])
    form_file = os.path.join(form_dir, "form.xhtml")

    if not os.path.exists(form_file):
        print(f"[WARN] File not found: {form_file}")
        return False

    with open(form_file, "r", encoding="utf-8") as f:
        xhtml_content = f.read()

    app = form_info["app"]
    form = form_info["form"]
    doc_id = form_info["doc_id"]
    title = form_info["title"]
    description = form_info["description"]

    metadata_xml = extract_metadata_xml(xhtml_content, app, form, title, description)

    builder_xml = f"""<form>
    <application-name>{app}</application-name>
    <form-name>{form}</form-name>
    <title xml:lang="pt">{title}</title>
    <description xml:lang="pt">{description}</description>
    <version-comment>1.0.0</version-comment>
</form>"""

    sql_script = f"""
-- 1. Form Runner definition
DELETE FROM orbeon_form_definition WHERE app = '{app}' AND form = '{form}';
DELETE FROM orbeon_form_definition_attach WHERE app = '{app}' AND form = '{form}';

INSERT INTO orbeon_form_definition (
    created, last_modified_time, last_modified_by, app, form, form_version, form_metadata, deleted, xml
) VALUES (
    NOW(), NOW(), 'admin', '{app}', '{form}', 1, $META${metadata_xml}$META$, 'N', $XML${xhtml_content}$XML$
);

INSERT INTO orbeon_form_definition_attach (
    created, last_modified_time, last_modified_by, app, form, form_version, deleted, file_name, file_content
) VALUES (
    NOW(), NOW(), 'admin', '{app}', '{form}', 1, 'N', 'form.xhtml', convert_to($XML${xhtml_content}$XML$, 'UTF8')
);

-- 2. Form Builder document (shows in Form Builder summary)
DELETE FROM orbeon_form_data WHERE app = 'orbeon' AND form = 'builder' AND document_id = '{doc_id}';
DELETE FROM orbeon_form_data_attach WHERE app = 'orbeon' AND form = 'builder' AND document_id = '{doc_id}';
DELETE FROM orbeon_i_current WHERE app = 'orbeon' AND form = 'builder' AND document_id = '{doc_id}';

INSERT INTO orbeon_form_data (
    created, last_modified_time, last_modified_by, username, app, form, form_version, stage, document_id, deleted, draft, xml
) VALUES (
    NOW(), NOW(), 'admin', 'admin', 'orbeon', 'builder', 1, 'published', '{doc_id}', 'N', 'N', $XML${builder_xml}$XML$
);

INSERT INTO orbeon_i_current (
    data_id, document_id, draft, app, form, form_version, stage, last_modified_time, created
) VALUES (
    (SELECT currval('orbeon_form_data_id_seq')), '{doc_id}', 'N', 'orbeon', 'builder', 1, 'published', NOW(), NOW()
);

INSERT INTO orbeon_form_data_attach (
    created, last_modified_time, last_modified_by, username, app, form, form_version, document_id, deleted, draft, file_name, file_content
) VALUES (
    NOW(), NOW(), 'admin', 'admin', 'orbeon', 'builder', 1, '{doc_id}', 'N', 'N', 'data.xml', convert_to($XML${xhtml_content}$XML$, 'UTF8')
);
"""

    res = subprocess.run(
        [
            "docker", "compose", "exec", "-T", "fzl-postgresql",
            "psql", "-U", "postgres", "-d", "orbeon"
        ],
        input=sql_script,
        text=True,
        capture_output=True
    )

    if res.returncode == 0:
        print(f"[OK] Published form: {app}/{form} ({title})")
        return True
    else:
        print(f"[ERROR] Failed to publish {app}/{form}: {res.stderr}")
        return False


def main():
    print("=== Publishing Orbeon Forms to PostgreSQL Database 'orbeon' ===")
    success_count = 0
    for form_info in FORMS:
        if publish_form(form_info):
            success_count += 1

    print(f"\nPublished {success_count}/{len(FORMS)} forms successfully.")
    print("\nForms available at:")
    print("  Form Runner Forms Home: https://fzlbpms.local/orbeon/fr/forms")
    print("  Form Builder Summary  : https://fzlbpms.local/orbeon/fr/orbeon/builder/summary")
    for form_info in FORMS:
        app = form_info["app"]
        form = form_info["form"]
        print(f"  - {form_info['title']}:")
        print(f"    Fill New Form: https://fzlbpms.local/orbeon/fr/{app}/{form}/new")


if __name__ == "__main__":
    main()
