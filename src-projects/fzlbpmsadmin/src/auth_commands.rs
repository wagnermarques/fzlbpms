use reqwest::Client;
use serde::{Deserialize, Serialize};
use tauri::command;

#[derive(Deserialize, Serialize)]
pub struct TokenResponse {
    pub access_token: String,
    pub refresh_token: String,
    pub expires_in: u64,
    pub token_type: String,
}

/// POST /fzlbpms/auth/login → TokenResponse
/// On HTTP error returns the status code as a string ("401", "500", …).
/// On network/connection error returns "0".
#[command]
pub async fn camel_login(username: String, password: String) -> Result<TokenResponse, String> {
    let client = Client::new();
    let resp = client
        .post("http://localhost:9090/fzlbpms/auth/login")
        .json(&serde_json::json!({ "username": username, "password": password }))
        .send()
        .await
        .map_err(|_| "0".to_string())?;

    let status = resp.status().as_u16();
    if !resp.status().is_success() {
        return Err(status.to_string());
    }

    resp.json::<TokenResponse>()
        .await
        .map_err(|_| "0".to_string())
}

/// POST /fzlbpms/auth/logout — fire and forget; errors are silently ignored.
#[command]
pub async fn camel_logout(refresh_token: String) -> Result<(), String> {
    let client = Client::new();
    let _ = client
        .post("http://localhost:9090/fzlbpms/auth/logout")
        .json(&serde_json::json!({ "refresh_token": refresh_token }))
        .send()
        .await;
    Ok(())
}
