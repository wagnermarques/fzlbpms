use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize)]
pub struct SiteInfo {
    pub sitename: String,
    pub siteurl: String,
    pub release: String,
    pub version: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct User {
    pub id: i32,
    pub username: String,
    pub fullname: String,
    pub email: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct Course {
    pub id: i32,
    pub fullname: String,
    pub shortname: String,
}

pub struct MoodleClient {
    moodle_url: String,
    token: String,
    client: reqwest::Client,
}

impl MoodleClient {
    pub fn new(moodle_url: String, token: String) -> Self {
        Self {
            moodle_url,
            token,
            client: reqwest::Client::new(),
        }
    }

    async fn call<T: for<'de> Deserialize<'de>>(
        &self,
        wsfunction: &str,
        params: &[(&str, &str)],
    ) -> Result<T, reqwest::Error> {
        let mut all_params = vec![
            ("wstoken", self.token.as_str()),
            ("wsfunction", wsfunction),
            ("moodlewsrestformat", "json"),
        ];
        all_params.extend_from_slice(params);

        self.client
            .get(format!("{}/webservice/rest/server.php", self.moodle_url))
            .query(&all_params)
            .send()
            .await?
            .json::<T>()
            .await
    }

    pub async fn get_site_info(&self) -> Result<SiteInfo, reqwest::Error> {
        self.call("core_webservice_get_site_info", &[]).await
    }

    pub async fn get_users_by_field(
        &self,
        field: &str,
        values: &[&str],
    ) -> Result<Vec<User>, reqwest::Error> {
        let value_params: Vec<String> = values
            .iter()
            .enumerate()
            .map(|(i, value)| format!("values[{}]={}", i, value))
            .collect();

        let query_string = value_params.join("&");

        let url = format!(
            "{}/webservice/rest/server.php?wstoken={}&wsfunction=core_user_get_users_by_field&moodlewsrestformat=json&field={}&{}",
            self.moodle_url, self.token, field, query_string
        );

        self.client.get(url).send().await?.json::<Vec<User>>().await
    }

    pub async fn get_courses(&self) -> Result<Vec<Course>, reqwest::Error> {
        self.call("core_course_get_courses", &[]).await
    }
}
