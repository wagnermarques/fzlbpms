//aplica windows_subsystem = "windows" to hide console window on Windows in release mode
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod moodle_api;
mod containers;
mod cmd;

use dotenv::dotenv;
use std::env;
use tauri::{Manager, Emitter};

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

            //only debug this code in debug mode
            #[cfg(debug_assertions)]
            {
                let window = app.get_webview_window("main").unwrap();
                window.open_devtools();
                window.close_devtools();
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            cmd::get_site_info,
            cmd::get_users,
            cmd::get_courses,
            containers::list_running_containers,
            containers::get_container_logs
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
