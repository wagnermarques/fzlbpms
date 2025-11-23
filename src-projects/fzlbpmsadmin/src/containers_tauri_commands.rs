use crate::containers::{self, Container};
use tauri::AppHandle;
use tauri_plugin_shell::ShellExt;

#[tauri::command]
/// List all running containers.
pub async fn list_running_containers() -> Result<Vec<Container>, String> {
    containers::list_running_containers().await
}

#[tauri::command]
/// Get logs for a specific container.
pub async fn get_container_logs(
    container_id: &str,
) -> Result<Vec<String>, String> {
    containers::get_container_logs(container_id).await
}

#[tauri::command]
/// Get the services from the docker-compose.yml file.
pub async fn get_docker_compose_services() -> Result<Vec<String>, String> {
    containers::get_docker_compose_services().await
}

#[tauri::command]
/// Run the docker-compose-up.sh script with the selected services.
pub async fn run_docker_compose_up(app: AppHandle, services: Vec<String>) -> Result<String, String> {
    let script_path = "/run/media/wgn/ext4/Projects-Srcs/fzlbpms/docker-compose-up.sh";
    let output = app.shell().command("sh")
        .arg(script_path)
        .args(services)
        .output()
        .await
        .map_err(|e| e.to_string())?;

    if output.status.success() {
        Ok(String::from_utf8_lossy(&output.stdout).to_string())
    } else {
        Err(String::from_utf8_lossy(&output.stderr).to_string())
    }
}
