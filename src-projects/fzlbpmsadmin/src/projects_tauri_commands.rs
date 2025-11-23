use crate::projects;

#[tauri::command]
/// List all projects.
pub async fn list_projects() -> Result<Vec<projects::Project>, String> {
    projects::list_projects().await
}
