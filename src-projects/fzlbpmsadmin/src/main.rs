#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod moodle_api;

use dotenv::dotenv;
use std::env;
use tauri::{Manager, Emitter};
use moodle_api::{MoodleClient, SiteInfo, User, Course};
use serde::Serialize;

#[derive(Debug, Serialize)]
pub enum CommandError {
    Moodle(String),
}

impl From<reqwest::Error> for CommandError {
    fn from(e: reqwest::Error) -> Self {
        CommandError::Moodle(e.to_string())
    }
}

#[tauri::command]
async fn get_site_info(moodle_url: &str, token: &str) -> Result<SiteInfo, CommandError> {
    let client = MoodleClient::new(moodle_url.to_string(), token.to_string());
    Ok(client.get_site_info().await?)
}

#[tauri::command]
async fn get_users(moodle_url: &str, token: &str) -> Result<Vec<User>, CommandError> {
    let client = MoodleClient::new(moodle_url.to_string(), token.to_string());
    // Example: get users by email
    Ok(client.get_users_by_field("email", &[""]).await?)
}

#[tauri::command]
async fn get_courses(moodle_url: &str, token: &str) -> Result<Vec<Course>, CommandError> {
    let client = MoodleClient::new(moodle_url.to_string(), token.to_string());
    Ok(client.get_courses().await?)
}

#[derive(Clone, serde::Serialize)]
struct MoodleConfig {
    url: String,
    token: String,
}

fn main() {
    dotenv().ok();

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_http::init())
        .setup(|app| {
            let moodle_url = env::var("MOODLE_URL").unwrap_or_else(|_| "https://your-moodle-site.com".to_string());
            let moodle_token = env::var("MOODLE_TOKEN").unwrap_or_else(|_| "your-moodle-token".to_string());

            app.get_webview_window("main").unwrap().emit("moodle_config", MoodleConfig {
                url: moodle_url,
                token: moodle_token,
            })?;

            #[cfg(debug_assertions)]
            {
                let window = app.get_webview_window("main").unwrap();
                window.open_devtools();
                window.close_devtools();
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![get_site_info, get_users, get_courses])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
