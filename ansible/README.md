# Ansible — Rust / Tauri toolchain setup

Installs everything needed to build and run the **fzlbpmsadmin** Tauri v2
desktop app (`src-projects/fzlbpmsadmin`): the system libraries
(webkit2gtk 4.1, GTK3, appindicator, OpenSSL headers, …) and the Rust
toolchain via rustup.

Supported targets: **Debian 12+**, **Ubuntu 22.04+**, **Fedora**, **Alpine 3.19+**.
(Older Debian/Ubuntu only ship webkit2gtk 4.0, which Tauri v2 cannot use.)

## Usage

```bash
cd ansible

# this machine (localhost is the default inventory entry)
ansible-playbook tauri-rust-setup.yml -K        # -K asks for the sudo password

# remote hosts: uncomment/add them in inventory.ini first
ansible-playbook tauri-rust-setup.yml -K -l debian-vm
```

After the first run, open a new shell (or `source ~/.cargo/env`) so `cargo`
is on your PATH, then:

```bash
cd ../src-projects/fzlbpmsadmin/angular-ui
npm run tauri:dev   # or: npx tauri dev
```

## Notes

- **Alpine**: needs the `community` repository enabled in
  `/etc/apk/repositories`, and `python3` installed for Ansible to manage it.
  The `apk` module comes from the `community.general` collection
  (`ansible-galaxy collection install community.general` if missing).
- **Rust** is installed per-user with rustup (stable toolchain), not from the
  distro packages, so every machine gets the same compiler version.
- The **Tauri CLI is not installed here** on purpose — it lives in the
  project as the `@tauri-apps/cli` npm devDependency, so `npm install` in
  `angular-ui` provides it.
- The playbook is idempotent: re-running it only updates the toolchain.
