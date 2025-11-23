use bollard::Docker;
use futures::stream::StreamExt;
use serde::{Deserialize, Serialize};
use bollard::query_parameters::{ListContainersOptionsBuilder, LogsOptionsBuilder};
use std::fs;
use serde_yaml;

#[derive(Debug, Serialize, Clone)]
pub struct Container {
    pub id: String,
    pub name: String,
    pub image: String,
    pub state: String,
    pub status: String,
}

#[derive(Debug, Deserialize)]
struct DockerCompose {
    services: serde_yaml::Mapping,
}

/// Connect to the Docker daemon.
pub async fn connect() -> Result<Docker, bollard::errors::Error> {
    Docker::connect_with_local_defaults()
}

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

/// Get the services from the docker-compose.yml file.
pub async fn get_docker_compose_services() -> Result<Vec<String>, String> {
    let path = "/run/media/wgn/ext4/Projects-Srcs/fzlbpms/docker-compose.yml";
    let file_content = fs::read_to_string(path).map_err(|e| e.to_string())?;
    let docker_compose: DockerCompose = serde_yaml::from_str(&file_content).map_err(|e| e.to_string())?;

    let services = docker_compose
        .services
        .keys()
        .map(|k| k.as_str().unwrap_or_default().to_string())
        .collect();

    Ok(services)
}
