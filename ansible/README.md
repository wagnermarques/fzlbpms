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
- **Local HTTPS & SSL Trust** (`tasks/local-https.yml`) — installs `mkcert`, trusts the root CA in the system store *and* in every browser certificate database it can find, issues the `fzlbpms.local` certificate, stages it plus the CA for the nginx/Moodle/Flowable/Karaf/oauth2-proxy/Theia containers, and restarts the ones that are running so they actually load it.

### Usage

```bash
cd ansible
ansible-playbook setup-project.yml -K        # -K prompts for sudo password
```

Or from the repo root (the `inventory.ini` path in `ansible.cfg` is relative to
this directory, so pass it explicitly from anywhere else):

```bash
ansible-playbook -i ansible/inventory.ini ansible/setup-project.yml -K
```

### Generated secrets

`docker-compose.yml` reads `FZL_KARAF_PASSWORD` as `${FZL_KARAF_PASSWORD:?}`, so
the stack refuses to start rather than fall back to a default password. On a
fresh clone the playbook seeds `.env` from `.env.template` and generates a
random 28-character value there, once — it never overwrites one that is already
set. That single line is the whole Karaf credential: the container writes its
JAAS realm (SSH console, JMX, and the HTTP Basic realm behind the web console
and Hawtio) from it at start, and nginx builds the `Authorization` header it
presents to `/system/console` and `/hawtio` from the same variable, so the two
sides cannot drift apart.

```bash
ansible-playbook -i ansible/inventory.ini ansible/setup-project.yml --tags secrets
```

To rotate: put a new value in `.env`, then
`docker compose up -d --force-recreate fzl-karaf-camel-integration fzl-nginx`.

### Certificates only

Re-issuing / re-trusting the local HTTPS certificate is tagged, so you can do
just that part without redoing the Docker and SELinux setup:

```bash
ansible-playbook -i ansible/inventory.ini ansible/setup-project.yml --tags certs -K
```

It is conditional, not unconditional: `mkcert` is only invoked when the staged
certificate is missing, signed by a different CA than the one currently
trusted, expiring within 30 days, or missing its `fzlbpms.local` SAN. When it
does re-issue, the running containers that consume the certificate or the CA
are restarted automatically — `docker compose up -d` will not do it for you,
because their config has not changed and each of them installs the certificate
from its *entrypoint*, i.e. only at container start.

Force a fresh certificate anyway:

```bash
ansible-playbook -i ansible/inventory.ini ansible/setup-project.yml \
    --tags certs -e fzlbpms_https_force_reissue=true -K
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
- `tasks/local-https.yml` is the single implementation of local HTTPS, included by `setup-project.yml`. There is no companion shell script — it replaced `bin/setup-local-https.sh`.
- Local configuration is targeted via `inventory.ini` (`localhost ansible_connection=local`).
