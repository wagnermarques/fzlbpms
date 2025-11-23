use std::fs;
use serde::{Serialize};

#[derive(Debug, Serialize, Clone)]
pub struct Project {
    pub name: String,
    pub url: String,
}

/// List all subdirectories in the given paths.
pub async fn list_projects() -> Result<Vec<Project>, String> {
    let mut projects = Vec::new();
    let paths = [
        "/run/media/wgn/ext4/Projects-Srcs/fzlbpms/src-projects",
        "/run/media/wgn/ext4/Projects-Srcs/fzlbpms/src-projects/var_www/html",
    ];

    for path in paths.iter() {
        let entries = fs::read_dir(path).map_err(|e| e.to_string())?;
        for entry in entries {
            let entry = entry.map_err(|e| e.to_string())?;
            if entry.file_type().map_err(|e| e.to_string())?.is_dir() {
                if let Some(name) = entry.file_name().to_str() {
                    projects.push(Project {
                        name: name.to_string(),
                        url: format!("http://localhost/{}", name),
                    });
                }
            }
        }
    }

    Ok(projects)
}
