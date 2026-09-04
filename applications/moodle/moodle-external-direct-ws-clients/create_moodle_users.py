#!/usr/bin/env python3
"""
Moodle User Import Script (Python)
----------------------------------
Reads student records from an Excel (.xlsx) file and creates Moodle users via Moodle's REST Web Service.

Requirements:
    pip install pandas openpyxl requests

Usage:
    python3 create_moodle_users.py --token <MOODLE_TOKEN> [options]
    or configure via environment variables:
    export MOODLE_URL="https://fzlbpms.com.br/moodle"
    export MOODLE_TOKEN="your_token_here"
    python3 create_moodle_users.py
"""

import os
import sys
import argparse
import requests
import pandas as pd


def parse_arguments():
    parser = argparse.ArgumentParser(description="Create Moodle users from an Excel list via Web Service.")
    parser.add_argument(
        "--file", "-f",
        default="/home/wgn/mnt/ext4/Projects-Srcs/Projects-Srcs-FzlSoft/fzlbpms/workspaces/fzlcoord/csvs-listas-de-alunos/lista_de_alunos_2mod_2026Sem2.xlsx",
        help="Path to Excel (.xlsx) file"
    )
    parser.add_argument(
        "--url", "-u",
        default=os.getenv("MOODLE_URL", "https://fzlbpms.com.br/moodle"),
        help="Moodle base URL (default: https://fzlbpms.com.br/moodle or $MOODLE_URL)"
    )
    parser.add_argument(
        "--token", "-t",
        default=os.getenv("MOODLE_TOKEN", ""),
        help="Moodle Web Service token (or set $MOODLE_TOKEN)"
    )
    parser.add_argument(
        "--email-domain", "-d",
        default=os.getenv("EMAIL_DOMAIN", "aluno.fzlbpms.com.br"),
        help="Email domain to generate email (default: aluno.fzlbpms.com.br)"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Parse and display users without making actual API requests"
    )
    return parser.parse_args()


def load_students_from_excel(file_path: str):
    """
    Parses the student Excel file looking for RM and NOME headers.
    """
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"Excel file not found: {file_path}")

    # Read the excel file without assuming header row index initially
    df_raw = pd.read_excel(file_path, header=None)

    # Locate the header row containing 'RM' and 'NOME'
    header_row_idx = None
    rm_col_idx = None
    nome_col_idx = None

    for idx, row in df_raw.iterrows():
        row_values = [str(val).strip().upper() for val in row.values if pd.notna(val)]
        if "RM" in row_values and "NOME" in row_values:
            header_row_idx = idx
            for col_idx, cell_value in enumerate(row.values):
                val = str(cell_value).strip().upper()
                if val == "RM":
                    rm_col_idx = col_idx
                elif val == "NOME":
                    nome_col_idx = col_idx
            break

    if header_row_idx is None or rm_col_idx is None or nome_col_idx is None:
        raise ValueError("Could not find 'RM' and 'NOME' header columns in the Excel file.")

    students = []
    for idx in range(header_row_idx + 1, len(df_raw)):
        rm_val = df_raw.iloc[idx, rm_col_idx]
        nome_val = df_raw.iloc[idx, nome_col_idx]

        if pd.isna(rm_val) or pd.isna(nome_val):
            continue

        rm_str = str(rm_val).strip()
        # If read as float e.g. 27387.0
        if rm_str.endswith(".0"):
            rm_str = rm_str[:-2]

        full_name = str(nome_val).strip()
        if not rm_str or not full_name:
            continue

        # Split into firstname and lastname
        name_parts = full_name.split()
        if len(name_parts) == 1:
            firstname = name_parts[0]
            lastname = name_parts[0]
        else:
            firstname = name_parts[0]
            lastname = " ".join(name_parts[1:])

        students.append({
            "rm": rm_str,
            "firstname": firstname,
            "lastname": lastname,
            "fullname": full_name
        })

    return students


def create_moodle_users(base_url: str, token: str, students: list, email_domain: str, dry_run: bool = False):
    """
    Calls core_user_create_users web service endpoint.
    Sets 'createpassword': 0, 'password': RM, and preference 'auth_forcepasswordchange': 1.
    """
    api_url = f"{base_url.rstrip('/')}/webservice/rest/server.php"

    payload = {
        "wstoken": token,
        "wsfunction": "core_user_create_users",
        "moodlewsrestformat": "json"
    }

    print(f"\n[+] Loaded {len(students)} student(s) from Excel file.")
    print("-" * 75)
    print(f"{'RM (Username)':<15} | {'Firstname':<20} | {'Lastname':<35}")
    print("-" * 75)

    for i, s in enumerate(students):
        username = s["rm"].lower()
        password = s["rm"]
        email = f"{username}@{email_domain}"

        print(f"{username:<15} | {s['firstname']:<20} | {s['lastname']:<35}")

        payload[f"users[{i}][username]"] = username
        payload[f"users[{i}][password]"] = password
        payload[f"users[{i}][firstname]"] = s["firstname"]
        payload[f"users[{i}][lastname]"] = s["lastname"]
        payload[f"users[{i}][email]"] = email
        payload[f"users[{i}][auth]"] = "manual"
        # Force password change at first login
        payload[f"users[{i}][preferences][0][type]"] = "auth_forcepasswordchange"
        payload[f"users[{i}][preferences][0][value]"] = "1"

    print("-" * 75)

    if dry_run:
        print("\n[INFO] Dry-run enabled. No requests sent to Moodle.")
        return

    if not token:
        print("\n[ERROR] Moodle token not provided! Use --token or set MOODLE_TOKEN environment variable.")
        sys.exit(1)

    print(f"\n[+] Sending request to {api_url} ...")
    try:
        response = requests.post(api_url, data=payload, timeout=30)
        response.raise_for_status()
        data = response.json()

        if isinstance(data, dict) and (data.get("exception") or data.get("errorcode")):
            print(f"\n[ERROR] Moodle API returned an error:")
            print(f"Message: {data.get('message')}")
            print(f"ErrorCode: {data.get('errorcode')}")
            if "debuginfo" in data:
                print(f"Debug: {data.get('debuginfo')}")
            sys.exit(1)

        print("\n[SUCCESS] Users created successfully:")
        for res in data:
            print(f" - ID: {res.get('id')}, Username: {res.get('username')}")

    except requests.RequestException as e:
        print(f"\n[ERROR] HTTP request failed: {e}")
        sys.exit(1)


def main():
    args = parse_arguments()
    try:
        students = load_students_from_excel(args.file)
        create_moodle_users(
            base_url=args.url,
            token=args.token,
            students=students,
            email_domain=args.email_domain,
            dry_run=args.dry_run
        )
    except Exception as e:
        print(f"[ERROR] {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
