use serde::Serialize;
use crate::moodle_api::{MoodleClient, SiteInfo, User, Course};

#[tauri::command]
pub fn fzlbpms_version() -> String {
    "v0.0.0!".into()
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

#[tauri::command]
pub async fn get_site_info(moodle_url: &str, token: &str) -> Result<SiteInfo, CommandError> {
    let client = MoodleClient::new(moodle_url.to_string(), token.to_string());
    Ok(client.get_site_info().await?)
}

#[tauri::command]
pub async fn get_users(moodle_url: &str, token: &str) -> Result<Vec<User>, CommandError> {
    let client = MoodleClient::new(moodle_url.to_string(), token.to_string());
    // Example: get users by email
    Ok(client.get_users_by_field("email", &[""]).await?)
}

#[tauri::command]
pub async fn get_courses(moodle_url: &str, token: &str) -> Result<Vec<Course>, CommandError> {
    let client = MoodleClient::new(moodle_url.to_string(), token.to_string());
    Ok(client.get_courses().await?)
}
