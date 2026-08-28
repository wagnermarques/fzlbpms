# Ansible Playbooks — fzlbpms

Automated host environment setup and developer toolchain provisioning for the **fzlbpms** platform.

Supported targets: **Fedora**, **Debian 12+**, **Ubuntu 22.04+**, **Alpine 3.19+**.

---

## 1. Project Onboarding (`setup-project.yml`)

The primary playbook to configure everything required to run the stack smoothly and securely:
- **Docker Engine + Compose v2** (Official repositories & group permissions).
- **Storage Driver Optimization** (`btrfs` native driver for Fedora Workstation).
- **SELinux Permissions** (Applies `container_file_t` to bind-mounted source folders).
- **Local Domain Resolution** (Maps `127.0.0.1 fzlbpms.local` in `/etc/hosts`).
- **Local HTTPS & SSL Trust** (Installs `mkcert`, trusts the root CA in system/browser NSS stores, and stages certificates for Keycloak/Nginx/OAuth2 containers).

### Usage

```bash
cd ansible
ansible-playbook setup-project.yml -K        # -K prompts for sudo password
```

After the playbook completes:
```bash
# 1. Refresh group if Docker was newly installed
newgrp docker

# 2. Start the project stack
cd ..
./bin/run-stack.sh basic

# 3. Open in your browser
https://fzlbpms.local/fzlbpmsadmin/
```

---

## 2. Desktop App Toolchain (`tauri-rust-setup.yml`)

Installs the Rust toolchain (via `rustup`) and system libraries (`webkit2gtk 4.1`, `GTK3`, `OpenSSL` headers) required to build and run the **fzlbpmsadmin** Tauri v2 desktop app (`src-projects/fzlbpmsadmin`).

### Usage

```bash
cd ansible
ansible-playbook tauri-rust-setup.yml -K
```

Then run the desktop app:
```bash
cd ../src-projects/fzlbpmsadmin/angular-ui
npm run tauri:dev
```

---

## Notes & Idempotency

- All playbooks in this directory are fully **idempotent**. You can safely re-run them at any time to verify or repair host configuration.
- Local configuration is targeted via `inventory.ini` (`localhost ansible_connection=local`).
