use anyhow::{bail, Context, Result};
use calamine::{open_workbook_auto, Data, Reader};
use clap::Parser;
use serde_json::Value;
use std::path::PathBuf;

#[derive(Parser, Debug)]
#[command(author, version, about = "Create Moodle users from an Excel file via Moodle Web Service", long_about = None)]
struct Args {
    /// Path to Excel (.xlsx) file
    #[arg(
        short,
        long,
        default_value = "/home/wgn/mnt/ext4/Projects-Srcs/Projects-Srcs-FzlSoft/fzlbpms/workspaces/fzlcoord/csvs-listas-de-alunos/lista_de_alunos_2mod_2026Sem2.xlsx"
    )]
    file: PathBuf,

    /// Moodle Base URL
    #[arg(
        short,
        long,
        env = "MOODLE_URL",
        default_value = "https://fzlbpms.com.br/moodle"
    )]
    url: String,

    /// Moodle Web Service Token
    #[arg(short, long, env = "MOODLE_TOKEN", default_value = "")]
    token: String,

    /// Email domain used to generate email addresses (e.g. aluno.fzlbpms.com.br)
    #[arg(
        short,
        long,
        env = "EMAIL_DOMAIN",
        default_value = "aluno.fzlbpms.com.br"
    )]
    email_domain: String,

    /// Parse and display users without sending request to Moodle
    #[arg(long)]
    dry_run: bool,
}

#[derive(Debug)]
struct Student {
    rm: String,
    firstname: String,
    lastname: String,
}

fn extract_students_from_excel(path: &PathBuf) -> Result<Vec<Student>> {
    let mut workbook = open_workbook_auto(path)
        .with_context(|| format!("Failed to open excel file: {:?}", path))?;

    let sheet_names = workbook.sheet_names().to_vec();
    if sheet_names.is_empty() {
        bail!("The Excel file does not contain any sheets.");
    }

    let range = workbook
        .worksheet_range(&sheet_names[0])
        .context("Failed to read first sheet")?;

    let mut header_row_idx = None;
    let mut rm_col_idx = None;
    let mut nome_col_idx = None;

    // Search for header row with 'RM' and 'NOME'
    for (row_idx, row) in range.rows().enumerate() {
        let mut found_rm = None;
        let mut found_nome = None;

        for (col_idx, cell) in row.iter().enumerate() {
            let text = match cell {
                Data::String(s) => s.trim().to_uppercase(),
                _ => String::new(),
            };
            if text == "RM" {
                found_rm = Some(col_idx);
            } else if text == "NOME" {
                found_nome = Some(col_idx);
            }
        }

        if let (Some(rm_c), Some(nome_c)) = (found_rm, found_nome) {
            header_row_idx = Some(row_idx);
            rm_col_idx = Some(rm_c);
            nome_col_idx = Some(nome_c);
            break;
        }
    }

    let header_idx = header_row_idx.context("Could not find 'RM' and 'NOME' header row in Excel sheet")?;
    let rm_idx = rm_col_idx.unwrap();
    let nome_idx = nome_col_idx.unwrap();

    let mut students = Vec::new();

    for row in range.rows().skip(header_idx + 1) {
        if row.len() <= rm_idx || row.len() <= nome_idx {
            continue;
        }

        let rm_str = match &row[rm_idx] {
            Data::String(s) => s.trim().to_string(),
            Data::Float(f) => format!("{:.0}", f),
            Data::Int(i) => i.to_string(),
            _ => String::new(),
        };

        let nome_str = match &row[nome_idx] {
            Data::String(s) => s.trim().to_string(),
            _ => String::new(),
        };

        if rm_str.is_empty() || nome_str.is_empty() {
            continue;
        }

        let parts: Vec<&str> = nome_str.split_whitespace().collect();
        let (firstname, lastname) = if parts.is_empty() {
            continue;
        } else if parts.len() == 1 {
            (parts[0].to_string(), parts[0].to_string())
        } else {
            (parts[0].to_string(), parts[1..].join(" "))
        };

        students.push(Student {
            rm: rm_str,
            firstname,
            lastname,
        });
    }

    Ok(students)
}

fn main() -> Result<()> {
    let args = Args::parse();

    let students = extract_students_from_excel(&args.file)?;

    println!("\n[+] Loaded {} student(s) from Excel file.", students.len());
    println!("{:-<75}", "");
    println!("{:<15} | {:<20} | {:<35}", "RM (Username)", "Firstname", "Lastname");
    println!("{:-<75}", "");

    for s in &students {
        println!("{:<15} | {:<20} | {:<35}", s.rm.to_lowercase(), s.firstname, s.lastname);
    }
    println!("{:-<75}", "");

    if args.dry_run {
        println!("\n[INFO] Dry-run enabled. No requests sent to Moodle.");
        return Ok(());
    }

    if args.token.is_empty() {
        bail!("Moodle token not provided! Please provide --token <TOKEN> or set MOODLE_TOKEN environment variable.");
    }

    let endpoint = format!("{}/webservice/rest/server.php", args.url.trim_end_matches('/'));

    let mut form_params: Vec<(String, String)> = Vec::new();
    form_params.push(("wstoken".to_string(), args.token.clone()));
    form_params.push(("wsfunction".to_string(), "core_user_create_users".to_string()));
    form_params.push(("moodlewsrestformat".to_string(), "json".to_string()));

    for (i, s) in students.iter().enumerate() {
        let username = s.rm.to_lowercase();
        let password = s.rm.clone();
        let email = format!("{}@{}", username, args.email_domain);

        form_params.push((format!("users[{}][username]", i), username));
        form_params.push((format!("users[{}][password]", i), password));
        form_params.push((format!("users[{}][firstname]", i), s.firstname.clone()));
        form_params.push((format!("users[{}][lastname]", i), s.lastname.clone()));
        form_params.push((format!("users[{}][email]", i), email));
        form_params.push((format!("users[{}][auth]", i), "manual".to_string()));
        // Force password change on first login
        form_params.push((format!("users[{}][preferences][0][type]", i), "auth_forcepasswordchange".to_string()));
        form_params.push((format!("users[{}][preferences][0][value]", i), "1".to_string()));
    }

    println!("\n[+] Sending request to {} ...", endpoint);

    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()?;

    let response = client
        .post(&endpoint)
        .form(&form_params)
        .send()
        .context("Failed to send request to Moodle Web Service")?;

    let status = response.status();
    let text = response.text().context("Failed to read response body")?;

    let json_val: Value = serde_json::from_str(&text)
        .with_context(|| format!("Failed to parse JSON response: {}", text))?;

    if let Some(map) = json_val.as_object() {
        if map.contains_key("exception") || map.contains_key("errorcode") {
            let msg = map.get("message").and_then(|v| v.as_str()).unwrap_or("Unknown error");
            let code = map.get("errorcode").and_then(|v| v.as_str()).unwrap_or("N/A");
            eprintln!("\n[ERROR] Moodle API returned an error:");
            eprintln!("Error Code: {}", code);
            eprintln!("Message: {}", msg);
            if let Some(debug) = map.get("debuginfo").and_then(|v| v.as_str()) {
                eprintln!("Debug: {}", debug);
            }
            bail!("Moodle Web Service call failed.");
        }
    }

    if let Some(users) = json_val.as_array() {
        println!("\n[SUCCESS] Users created successfully (status {}):", status);
        for u in users {
            let id = u.get("id").map(|v| v.to_string()).unwrap_or_default();
            let uname = u.get("username").and_then(|v| v.as_str()).unwrap_or_default();
            println!(" - ID: {}, Username: {}", id, uname);
        }
    } else {
        println!("\n[RESPONSE] {}", json_val);
    }

    Ok(())
}
