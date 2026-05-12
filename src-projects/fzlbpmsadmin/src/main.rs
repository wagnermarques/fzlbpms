//aplica windows_subsystem = "windows" to hide console window on Windows in release mode
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod moodle_api;
mod containers;
mod cmd;
mod containers_tauri_commands;
mod projects;
mod projects_tauri_commands;

use dotenv::dotenv;
use std::env;
use tauri::{Manager, Emitter, AppHandle};


#[derive(Clone, serde::Serialize)]
struct MoodleConfig {
    url: String,
    token: String,
}

#[derive(Clone, serde::Serialize)]
struct LogPayload {
    message: String,
    level: String,
    timestamp: String,
}

fn setup_logger(app_handle: AppHandle) -> Result<(), fern::InitError> {
    let frontend_handle = app_handle.clone();

    fern::Dispatch::new()
        .format(move |out, message, record| {
            out.finish(format_args!(
                "[{}][{}] {}",
                chrono::Local::now().format("%Y-%m-%d %H:%M:%S"),
                record.level(),
                message
            ))
        })
        .level(log::LevelFilter::Debug)
        // Chain a dispatch that prints to the console
        .chain(std::io::stdout())
        // Chain a dispatch that sends logs to the frontend
        .chain(fern::Dispatch::new()
            .level(log::LevelFilter::Info) // Only send Info and above to frontend
            .chain(fern::Output::call(move |record| {
                let payload = LogPayload {
                    message: record.args().to_string(),
                    level: record.level().to_string(),
                    timestamp: chrono::Local::now().to_string(),
                };
                // Emit the payload as a struct. Tauri will serialize it.
                let _ = frontend_handle.emit("log-message", payload);
            }))
        )
        .apply()?;

    Ok(())
}


fn main() {
    dotenv().ok();

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_http::init())
        .setup(|app| {
            // Setup the logger
            if let Err(e) = setup_logger(app.handle().clone()) {
                eprintln!("Error setting up logger: {}", e);
            }

            log::info!("Logger initialized.");

            let moodle_url = env::var("MOODLE_URL").unwrap_or_else(|_| "https://your-moodle-site.com".to_string());
            let moodle_token = env::var("MOODLE_TOKEN").unwrap_or_else(|_| "your-moodle-token".to_string());

            log::info!("Emitting Moodle config to the frontend.");
            app.get_webview_window("main").unwrap().emit("moodle_config", MoodleConfig {
                url: moodle_url,
                token: moodle_token,
            })?;

            //only debug this code in debug mode
            #[cfg(debug_assertions)]
            {
                log::debug!("Opening devtools.");
                let window = app.get_webview_window("main").unwrap();
                window.open_devtools();
                // window.close_devtools(); // Closing it immediately might not be what you want for debugging
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            cmd::fzlbpms_version,
            cmd::get_fzlbpms_home,
            cmd::get_site_info,
            cmd::get_users,
            cmd::get_courses,
            containers_tauri_commands::list_running_containers,
            containers_tauri_commands::get_container_logs,
            containers_tauri_commands::get_docker_compose_services,
            containers_tauri_commands::get_docker_compose_services_with_status,
            containers_tauri_commands::run_docker_compose_up,
            containers_tauri_commands::run_docker_compose_stop,
            projects_tauri_commands::list_projects,
            cmd::install_moodle
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
