use serde::{Deserialize, Serialize};
use crate::moodle_api::{MoodleClient, SiteInfo, User, Course};
use tauri::{AppHandle, Emitter};
use std::env;
use std::path::Path;
use std::fs;

use git2::Repository;
use tauri_plugin_shell::ShellExt;
use tempfile;

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

impl From<git2::Error> for CommandError {
    fn from(e: git2::Error) -> Self {
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
    db_type: String,
    db_host: String,
    db_name: String,
    db_user: String,
    db_pass: String,
    db_prefix: String,
    wwwroot: String,
    admin_user: String,
    admin_pass: String,
    admin_email: String,
    fullname: String,
    shortname: String,
    moodle_path: String,
    moodledata_path: String,
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

async fn create_config_php(app: &AppHandle, moodle_path: &Path, _moodledata_path: &Path, config: &MoodleConfig) -> Result<(), CommandError> {
    let config_php_path = moodle_path.join("config.php");
    let temp_dir = tempfile::tempdir()?;
    let temp_config_path = temp_dir.path().join("config.php");

    let config_content = format!(
        r#"<?php
unset($CFG);
global $CFG;
$CFG = new stdClass();

$CFG->dbtype    = '{}';
$CFG->dblibrary = 'native';
$CFG->dbhost    = '{}';
$CFG->dbname    = '{}';
$CFG->dbuser    = '{}';
$CFG->dbpass    = '{}';
$CFG->prefix    = '{}';
$CFG->dboptions = array(
    'dbpersist' => false,
    'dbsocket'  => false,
    'dbport'    => '',
);

$CFG->wwwroot   = '{}';
$CFG->dataroot  = '{}';
$CFG->admin     = 'admin';

$CFG->directorypermissions = 02777;

require_once(__DIR__ . '/lib/setup.php');
"#,
        config.db_type,
        config.db_host,
        config.db_name,
        config.db_user,
        config.db_pass,
        config.db_prefix,
        config.wwwroot,
        "/var/www/moodledata"
    );

    fs::write(&temp_config_path, config_content)?;

    let move_command = format!(
        "mv {} {} && chmod 600 {}",
        temp_config_path.display(),
        config_php_path.display(),
        config_php_path.display()
    );

    let output = app.shell().command("pkexec")
        .arg("sh")
        .arg("-c")
        .arg(move_command)
        .output()
        .await?;

    if !output.status.success() {
        let error_str = String::from_utf8_lossy(&output.stderr);
        return Err(CommandError::Moodle(format!(
            "Failed to move and set permissions for config.php: {}",
            error_str
        )));
    }

    Ok(())
}

#[tauri::command]
pub async fn install_moodle(app: AppHandle, config: MoodleConfig) -> Result<String, CommandError> {
    log::info!("Moodle installation started.");
    app.emit("moodle-installation-progress", "Moodle installation started.").unwrap();

    let fzlbpms_home = env::var("FZLBPMS_HOME").map_err(|e| CommandError::Moodle(e.to_string()))?;
    
    // Create database
    let msg = "Creating Moodle database and user...";
    log::info!("{}", msg);
    app.emit("moodle-installation-progress", msg).unwrap();

    let script_path = Path::new(&fzlbpms_home).join("bin/moodle/create-db-in-postgresql-container.sh");

    let output = app.shell().command(script_path.to_str().unwrap())
        .arg(&config.db_name)
        .arg(&config.db_user)
        .arg(&config.db_pass)
        .output()
        .await?;
    
    if output.status.success() {
        let output_str = String::from_utf8_lossy(&output.stdout);
        app.emit("moodle-installation-progress", &output_str).unwrap();
        let msg = "Database and user configured successfully.";
        log::info!("{}", msg);
        app.emit("moodle-installation-progress", msg).unwrap();
    } else {
        let error_str = String::from_utf8_lossy(&output.stderr);
        app.emit("moodle-installation-progress", &error_str).unwrap();
        return Err(CommandError::Moodle(format!("Database creation failed: {}", error_str)));
    }

    let moodle_path = Path::new(&config.moodle_path);
    let moodledata_path = Path::new(&config.moodledata_path);

    if moodle_path.exists() {
        let msg = "Moodle directory already exists. Skipping clone.";
        log::info!("{}", msg);
        app.emit("moodle-installation-progress", msg).unwrap();
    } else {
        let msg = "Cloning Moodle repository...";
        log::info!("{}", msg);
        app.emit("moodle-installation-progress", msg).unwrap();

        let url = "https://github.com/moodle/moodle.git";
        Repository::clone(url, &moodle_path).map_err(|e| {
            CommandError::Moodle(format!(
                "Failed to clone Moodle repository to '{}': {}. Please ensure you have write permissions to the parent directory.",
                moodle_path.display(),
                e
            ))
        })?;

        let msg = "Moodle repository cloned successfully.";
        log::info!("{}", msg);
        app.emit("moodle-installation-progress", msg).unwrap();
    }

    let msg = "Creating config.php...";
    log::info!("{}", msg);
    app.emit("moodle-installation-progress", msg).unwrap();

    create_config_php(&app, &moodle_path, &moodledata_path, &config).await?;

    let msg = "config.php created successfully.";
    log::info!("{}", msg);
    app.emit("moodle-installation-progress", msg).unwrap();

    let msg = "Setting permissions for Moodle directory (requires admin)...";
    log::info!("{}", msg);
    app.emit("moodle-installation-progress", msg).unwrap();

    let moodle_path_str = moodle_path.to_str().ok_or_else(|| CommandError::Moodle("Moodle path contains invalid UTF-8".to_string()))?;
    let perm_command = format!("find '{}' -type d -exec chmod 755 {{}} + && find '{}' -type f -exec chmod 644 {{}} +", moodle_path_str, moodle_path_str);
    
    let output = app.shell().command("pkexec")
        .arg("sh")
        .arg("-c")
        .arg(perm_command)
        .output()
        .await?;

    if !output.status.success() {
        let error_str = String::from_utf8_lossy(&output.stderr);
        let err_msg = format!("Failed to set Moodle directory permissions: {}", error_str);
        app.emit("moodle-installation-progress", &err_msg).unwrap();
        return Err(CommandError::Moodle(err_msg));
    }
    
    let msg = "Moodle directory permissions set.";
    log::info!("{}", msg);
    app.emit("moodle-installation-progress", msg).unwrap();


    if moodledata_path.exists() {
        let msg = "Moodle data directory already exists. Skipping creation.";
        log::info!("{}", msg);
        app.emit("moodle-installation-progress", msg).unwrap();
    } else {
        let msg = "Creating Moodle data directory...";
        log::info!("{}", msg);
        app.emit("moodle-installation-progress", msg).unwrap();

        fs::create_dir_all(&moodledata_path).map_err(|e| {
            CommandError::Moodle(format!(
                "Failed to create Moodle data directory at '{}': {}. Please ensure you have write permissions to the parent directory.",
                moodledata_path.display(),
                e
            ))
        })?;

        let msg = "Moodle data directory created successfully.";
        log::info!("{}", msg);
        app.emit("moodle-installation-progress", msg).unwrap();
    }

    let msg = "Setting permissions for Moodle data directory...";
    log::info!("{}", msg);
    app.emit("moodle-installation-progress", msg).unwrap();
    let moodledata_path_str = moodledata_path.to_str().ok_or_else(|| CommandError::Moodle("Moodle data path contains invalid UTF-8".to_string()))?;
    let perm_command = format!("chmod 775 '{}'", moodledata_path_str);

    let output = app.shell().command("pkexec")
        .arg("sh")
        .arg("-c")
        .arg(perm_command)
        .output()
        .await?;

    if !output.status.success() {
        let error_str = String::from_utf8_lossy(&output.stderr);
        let err_msg = format!("Failed to set Moodle data directory permissions: {}", error_str);
        app.emit("moodle-installation-progress", &err_msg).unwrap();
        return Err(CommandError::Moodle(err_msg));
    }
    let msg = "Moodle data directory permissions set to 775.";
    log::info!("{}", msg);
    app.emit("moodle-installation-progress", msg).unwrap();

    let msg = "Installing Moodle...";
    log::info!("{}", msg);
    app.emit("moodle-installation-progress", msg).unwrap();

    // Run installation inside the container
    // We assume the standard path /var/www/html/moodle based on docker-compose mount
    let install_command = format!(
        "docker exec -w /var/www/html/moodle fzl-php8.3-fpm php admin/cli/install_database.php --agree-license --lang=en --adminuser={} --adminpass={} --adminemail={} --fullname='{}' --shortname='{}'",
        config.admin_user,
        config.admin_pass,
        config.admin_email,
        config.fullname,
        config.shortname
    );

    let output = app.shell().command("pkexec")
        .arg("sh")
        .arg("-c")
        .arg(install_command)
        .output()
        .await?;

    if output.status.success() {
        let output_str = String::from_utf8_lossy(&output.stdout);
        app.emit("moodle-installation-progress", &output_str).unwrap();
        let msg = "Moodle installed successfully.";
        log::info!("{}", msg);
        app.emit("moodle-installation-progress", msg).unwrap();
    } else {
        let error_str = String::from_utf8_lossy(&output.stderr);
        app.emit("moodle-installation-progress", &error_str).unwrap();
        return Err(CommandError::Moodle(format!("Moodle installation failed: {}", error_str)));
    }

    let final_msg = "Moodle installation process completed.";
    app.emit("moodle-installation-progress", final_msg).unwrap();
    Ok(final_msg.to_string())
}