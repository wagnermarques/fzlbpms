# Moodle User Admin Scripts

Scripts to batch create Moodle users from student Excel rosters using the Moodle REST Web Service (`core_user_create_users`).

## Requirements on Moodle
1. Enable Web Services under **Site administration > Server > Web services > Overview**.
2. Enable REST protocol under **Site administration > Server > Web services > Manage protocols**.
3. Create an external service or use an existing service with the function `core_user_create_users`.
4. Generate a token for your admin user under **Site administration > Server > Web services > Manage tokens**.

---

## 1. Python Script

### Location:
[`create_moodle_users.py`](file:///home/wgn/mnt/ext4/Projects-Srcs/Projects-Srcs-FzlSoft/fzlbpms/applications/moodle/create_moodle_users.py)

### Requirements:
```bash
pip install pandas openpyxl requests
```

### Usage:
```bash
# Dry run (test extraction without calling Moodle API):
python3 create_moodle_users.py --dry-run

# Run creation:
python3 create_moodle_users.py --token "YOUR_MOODLE_WEBSERVICE_TOKEN"

# Optional parameters:
python3 create_moodle_users.py \
    --file "/path/to/lista_de_alunos.xlsx" \
    --url "https://fzlbpms.com.br/moodle" \
    --token "YOUR_MOODLE_WEBSERVICE_TOKEN" \
    --email-domain "aluno.fzlbpms.com.br"
```

---

## 2. Rust Application

### Location:
[`moodle_user_creator`](file:///home/wgn/mnt/ext4/Projects-Srcs/Projects-Srcs-FzlSoft/fzlbpms/applications/moodle/moodle_user_creator)

### Build:
```bash
cd moodle_user_creator
cargo build --release
```

### Usage:
```bash
# Dry run:
./target/release/moodle_user_creator --dry-run

# Run creation:
./target/release/moodle_user_creator --token "YOUR_MOODLE_WEBSERVICE_TOKEN"

# Or using environment variables:
export MOODLE_URL="https://fzlbpms.com.br/moodle"
export MOODLE_TOKEN="YOUR_MOODLE_WEBSERVICE_TOKEN"
./target/release/moodle_user_creator
```

---

## User Configuration Summary
- **Username**: RM (lowercased)
- **Password**: RM
- **First Name & Last Name**: Extracted from column `NOME` (First word -> First name, remaining -> Last name)
- **Email**: `{RM}@aluno.fzlbpms.com.br` (customizable with `--email-domain`)
- **Force Password Change**: User is configured with `auth_forcepasswordchange = 1` requiring password update on first login.
