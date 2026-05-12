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

#[derive(Debug, Serialize, Clone)]
pub struct DockerComposeService {
    pub name: String,
    pub container_name: String,
    pub container_id: Option<String>,
    pub state: Option<String>,
    pub status: Option<String>,
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
            name: c.names.unwrap_or_default().join(", ").trim_start_matches('/').to_string(),
            image: c.image.unwrap_or_default(),
            state: c.state.map(|s| s.to_string()).unwrap_or_default(),
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

use std::env;

/// Get the services from the docker-compose.yml file.
pub async fn get_docker_compose_services() -> Result<Vec<String>, String> {
    let fzlbpms_home = env::var("FZLBPMS_HOME").map_err(|e| e.to_string())?;
    let path = format!("{}/docker-compose.yml", fzlbpms_home);
    let file_content = fs::read_to_string(&path).map_err(|e| format!("Failed to read {}: {}", path, e))?;
    let docker_compose: DockerCompose = serde_yaml::from_str(&file_content).map_err(|e| e.to_string())?;

    let services = docker_compose
        .services
        .keys()
        .map(|k| k.as_str().unwrap_or_default().to_string())
        .collect();

    Ok(services)
}

/// Get the services from the docker-compose.yml file with their status.
pub async fn get_docker_compose_services_with_status() -> Result<Vec<DockerComposeService>, String> {
    let fzlbpms_home = env::var("FZLBPMS_HOME").map_err(|e| e.to_string())?;
    let path = format!("{}/docker-compose.yml", fzlbpms_home);
    let file_content = fs::read_to_string(&path).map_err(|e| format!("Failed to read {}: {}", path, e))?;
    let docker_compose: DockerCompose = serde_yaml::from_str(&file_content).map_err(|e| e.to_string())?;

    let running_containers = list_running_containers().await?;

    let mut services_status = Vec::new();

    for (key, value) in docker_compose.services {
        let service_name = key.as_str().unwrap_or_default().to_string();
        
        // Try to get container_name from yaml, otherwise use service_name
        let container_name = value.as_mapping()
            .and_then(|m| m.get(&serde_yaml::Value::String("container_name".to_string())))
            .and_then(|v| v.as_str())
            .unwrap_or(&service_name)
            .to_string();

        let container = running_containers.iter().find(|c| {
            // Match by exact name or if the container name contains the service name (docker compose often prefixes)
            c.name == container_name || c.name.contains(&format!("_{}_", service_name))
        });

        services_status.push(DockerComposeService {
            name: service_name,
            container_name,
            container_id: container.map(|c| c.id.clone()),
            state: container.map(|c| c.state.clone()),
            status: container.map(|c| c.status.clone()),
        });
    }

    Ok(services_status)
}
