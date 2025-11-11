use bollard::Docker;
use futures::stream::StreamExt;
use serde::Serialize;
use bollard::query_parameters::{ListContainersOptionsBuilder, LogsOptionsBuilder};

#[derive(Debug, Serialize)]
pub struct Container {
    pub id: String,
    pub name: String,
    pub image: String,
    pub state: String,
    pub status: String,
}

/// Connect to the Docker daemon.
pub async fn connect() -> Result<Docker, bollard::errors::Error> {
    Docker::connect_with_local_defaults()
}

#[tauri::command]
/// List all running containers.
pub async fn list_running_containers() -> Result<Vec<Container>, String> {
    let docker = connect().await.map_err(|e| e.to_string())?;
    let options = Some(ListContainersOptionsBuilder::default().all(true).build());

    let containers = docker.list_containers(options).await.map_err(|e| e.to_string())?;
    let result = containers
        .into_iter()
        .map(|c| Container {
            id: c.id.unwrap_or_default(),
            name: c.names.unwrap_or_default().join(", "),
            image: c.image.unwrap_or_default(),
            state: c.state.map(|s| format!("{:?}", s)).unwrap_or_default(),
            status: c.status.unwrap_or_default(),
        })
        .collect();
    Ok(result)
}

#[tauri::command]
/// Get logs for a specific container.
pub async fn get_container_logs(
    container_id: &str,
) -> Result<Vec<String>, String> {
    let docker = connect().await.map_err(|e| e.to_string())?;
    let options = Some(LogsOptionsBuilder::default().stdout(true).stderr(true).build());

    let mut stream = docker.logs(container_id, options);
    let mut logs = Vec::new();

    while let Some(log) = stream.next().await {
        match log {
            Ok(log_entry) => logs.push(log_entry.to_string()),
            Err(e) => return Err(e.to_string()),
        }
    }

    Ok(logs)
}
