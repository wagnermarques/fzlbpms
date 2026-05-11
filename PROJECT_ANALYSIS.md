# fzlbpms Project Analysis

## Overview
**fzlbpms** (FZL Business Process Management System) is a comprehensive suite of tools and containers designed for BPMS workflows. The core of the development experience is **fzlbpmsadmin**, a desktop administration GUI built with **Tauri v2**, combining a **Rust** backend with an **Angular** frontend.

## Project Structure
The root directory `/home/wgn/mnt/kvm_share/fzlbpms` contains the infrastructure and source code.

*   **`src-projects/fzlbpmsadmin`**: The main Admin GUI application (Tauri).
    *   **`src/`**: Rust backend source code.
    *   **`angular-ui/`**: Angular frontend source code.
*   **`docker-compose.yml`**: Defines the container orchestration (PostgreSQL, Nginx, PHP, etc.).
*   **`bin/`**: Scripts and utilities.

## Architecture: fzlbpmsadmin (Admin GUI)
The application follows the Tauri architecture:
*   **Native Shell**: Managed by Tauri (Rust), handling window creation and OS integration.
*   **Backend (Rust)**: Handles heavy lifting, system operations (Docker, File I/O), and API communication.
*   **Frontend (Angular)**: Provides the user interface, communicating with Rust via Tauri's IPC mechanism.

### Frontend Analysis (Angular)
Located in `src-projects/fzlbpmsadmin/angular-ui`.

*   **Framework**: Angular v20.3.0 (Latest/Bleeding Edge).
*   **UI Library**: Angular Material v20.2.12.
*   **Tauri Integration**: `@tauri-apps/api` v2.9.0.
*   **Build System**: Angular CLI (`ng`), integrated with Tauri's build process.
*   **Key Scripts**:
    *   `npm run tauri:dev`: Runs Angular in watch mode + Tauri dev concurrently.
    *   `npm run tauri:build`: Builds the Angular app and bundles it with the Rust binary.

### Backend Analysis (Rust/Tauri)
Located in `src-projects/fzlbpmsadmin`.

*   **Tauri Version**: v2.0.0-rc.13 (Release Candidate).
*   **Entry Point**: `src/main.rs`.
*   **Key Dependencies**:
    *   `tokio`: Async runtime.
    *   `reqwest`: HTTP client (likely for Moodle API).
    *   `bollard`: Docker client for managing containers.
    *   `fern` / `log`: Logging infrastructure (custom implementation sends logs to Frontend via events).

*   **Modules**:
    *   `cmd.rs`: General commands and Moodle interactions.
    *   `containers*.rs`: Docker container management (listing, logs, compose up).
    *   `projects*.rs`: Project file management.
    *   `moodle_api.rs`: Moodle REST API wrapper.
    *   `main.rs`: Application bootstrapping, logging setup, and command registration.

*   **Exposed Commands**:
    The backend exposes several commands to the frontend:
    *   `fzlbpms_version`, `get_fzlbpms_home`, `get_site_info`
    *   `containers_tauri_commands::list_running_containers`
    *   `projects_tauri_commands::list_projects`
    *   `cmd::install_moodle`

## Infrastructure & Containers
The root `docker-compose.yml` manages the supporting services for the BPMS environment.
Based on the `README.org`, services include:
*   `fzl-nginx`: Web server / Reverse proxy.
*   `fzl-php8.3-fpm`: PHP runtime (for Moodle/Web apps).
*   `fzl-postgresql`: Database.
*   **`fzl-keycloak`**: Centralized Identity and Access Management (IAM).
*   `fzl-portainer`: Container management UI.
*   `fzl-nexus`: Artifact repository.

## Getting Started (Development)
1.  **Prerequisites**: Docker, Docker Compose, Node.js, Rust.
2.  **Start Infrastructure**:
    ```bash
    docker compose up --build fzl-nginx fzl-php8.3-fpm fzl-postgresql
    ```
3.  **Run Admin GUI**:
    ```bash
    cd src-projects/fzlbpmsadmin/angular-ui
    npm install
    npm run tauri:dev
    ```
