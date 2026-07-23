use serde::{Deserialize, Serialize};
use crate::moodle_api::{MoodleClient, SiteInfo, User, Course};
use tauri::{AppHandle, Emitter};
use std::env;
use std::path::PathBuf;

use tauri_plugin_shell::ShellExt;

#[tauri::command]
pub fn fzlbpms_version() -> String {
    "v0.0.0!".into()
}

#[tauri::command]
pub fn get_fzlbpms_home() -> Result<String, String> {
    env::var("FZLBPMS_HOME").map_err(|e| e.to_string())
}

#[derive(Debug, Serialize)]
pub enum CommandError {
    Moodle(String),
}

impl From<reqwest::Error> for CommandError {
    fn from(e: reqwest::Error) -> Self {
        CommandError::Moodle(e.to_string())
    }
}

impl From<std::io::Error> for CommandError {
    fn from(e: std::io::Error) -> Self {
        CommandError::Moodle(e.to_string())
    }
}

impl From<tauri_plugin_shell::Error> for CommandError {
    fn from(e: tauri_plugin_shell::Error) -> Self {
        CommandError::Moodle(e.to_string())
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MoodleConfig {
    #[serde(default)]
    db_host: String,
    db_name: String,
    db_user: String,
    db_pass: String,
    #[serde(default)]
    db_prefix: String,
    wwwroot: String,
    admin_user: String,
    admin_pass: String,
    admin_email: String,
    fullname: String,
    shortname: String,
}


#[tauri::command]
pub async fn get_site_info(moodle_url: &str, token: &str) -> Result<SiteInfo, CommandError> {
    let client = MoodleClient::new(moodle_url.to_string(), token.to_string());
    Ok(client.get_site_info().await?)
}

#[tauri::command]
pub async fn get_users(moodle_url: &str, token: &str) -> Result<Vec<User>, CommandError> {
    let client = MoodleClient::new(moodle_url.to_string(), token.to_string());
    Ok(client.get_users_by_field("email", &[""]).await?)
}

#[tauri::command]
pub async fn get_courses(moodle_url: &str, token: &str) -> Result<Vec<Course>, CommandError> {
    let client = MoodleClient::new(moodle_url.to_string(), token.to_string());
    Ok(client.get_courses().await?)
}

#[tauri::command]
pub async fn install_moodle(app: AppHandle, config: MoodleConfig) -> Result<String, CommandError> {
    log::info!("Moodle installation started.");
    app.emit("moodle-installation-progress", "Moodle installation started.").unwrap();

    // The install logic now lives in a single place: the idempotent one-shot
    // `moodle-installer` compose service (bin/moodle/install-moodle-in-container.sh).
    // The GUI just triggers it and relays the output, so the desktop app and
    // `./bin/run-stack.sh basic` share exactly one install code path — no more
    // host-side git clone, pkexec prompts, or hand-written config.php.
    let fzlbpms_home = env::var("FZLBPMS_HOME").map_err(|e| CommandError::Moodle(e.to_string()))?;

    let db_host = if config.db_host.trim().is_empty() {
        "fzl-postgresql".to_string()
    } else {
        config.db_host.clone()
    };

    let msg = "Running containerized Moodle installer (docker compose up moodle-installer)...";
    log::info!("{}", msg);
    app.emit("moodle-installation-progress", msg).unwrap();

    // Form values are passed as environment overrides. docker compose gives the
    // process environment precedence over the .env file, while the DB superuser
    // credentials and MOODLE_VERSION continue to come from .env.
    let mut command = app.shell().command("docker")
        .args(["compose", "up", "--build", "--no-log-prefix", "moodle-installer"])
        .current_dir(PathBuf::from(&fzlbpms_home))
        .env("MOODLE_DB_HOST", &db_host)
        .env("MOODLE_DB_NAME", &config.db_name)
        .env("MOODLE_DB_USER", &config.db_user)
        .env("MOODLE_DB_PASS", &config.db_pass)
        .env("MOODLE_WWWROOT", &config.wwwroot)
        .env("MOODLE_ADMIN_USER", &config.admin_user)
        .env("MOODLE_ADMIN_PASS", &config.admin_pass)
        .env("MOODLE_ADMIN_EMAIL", &config.admin_email)
        .env("MOODLE_FULLNAME", &config.fullname)
        .env("MOODLE_SHORTNAME", &config.shortname);

    if !config.db_prefix.trim().is_empty() {
        command = command.env("MOODLE_DB_PREFIX", &config.db_prefix);
    }

    let output = command.output().await?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);
    if !stdout.trim().is_empty() {
        app.emit("moodle-installation-progress", stdout.as_ref()).unwrap();
    }
    if !stderr.trim().is_empty() {
        app.emit("moodle-installation-progress", stderr.as_ref()).unwrap();
    }

    if output.status.success() {
        let final_msg = format!("Moodle installation completed. Access it at {}", config.wwwroot);
        log::info!("{}", final_msg);
        app.emit("moodle-installation-progress", &final_msg).unwrap();
        Ok(final_msg)
    } else {
        let err = format!("Moodle installer failed: {}", stderr);
        app.emit("moodle-installation-progress", &err).unwrap();
        Err(CommandError::Moodle(err))
    }
}
