#!/usr/bin/env python3
"""
PreToolUse Hook for Antigravity / Gemini CLI.
Auto-approves harmless read-only bash commands and GitHub curl/API requests.
"""

import sys
import json
import re
import shlex

SAFE_GITHUB_DOMAINS = [
    r"github\.com",
    r"api\.github\.com",
    r"raw\.githubusercontent\.com",
    r"gist\.githubusercontent\.com",
    r"objects\.githubusercontent\.com",
    r"codeload\.github\.com",
]

SAFE_STANDALONE_COMMANDS = {
    "pwd", "whoami", "id", "uptime", "date", "uname", "env", "printenv",
}

SAFE_BASE_COMMANDS = {
    "ls", "dir", "tree", "vdir",
    "head", "tail", "cat", "less", "more", "nl", "tac",
    "grep", "egrep", "fgrep", "rg", "ag",
    "find", "fd", "locate",
    "which", "whereis", "type",
    "stat", "file", "wc", "diff", "cmp", "md5sum", "sha256sum", "sha1sum",
    "jq", "yq", "sort", "uniq", "cut", "tr", "column", "awk",
    "echo", "printf",
    "node -v", "node --version", "npm -v", "npm --version",
    "python3 --version", "python --version", "python3 -m json.tool",
    "docker ps", "docker compose ps",
}

SAFE_GIT_SUBCOMMANDS = {
    "status", "log", "diff", "show", "branch", "tag", "rev-parse",
    "remote", "describe", "config", "check-ignore", "ls-files", "stash list",
}

SAFE_GH_SUBCOMMANDS = {
    "repo view", "issue list", "issue view", "pr list", "pr view",
    "run list", "run view", "run watch", "api", "search prs", "search issues",
    "search commits", "search code",
}


def is_safe_curl_or_wget(cmd_str: str) -> bool:
    """Check if curl or wget command only accesses approved GitHub domains."""
    # Match URL in the command
    urls = re.findall(r"https?://[^\s\"']+", cmd_str)
    if not urls:
        return False

    domain_pattern = re.compile(
        r"^https?://([a-zA-Z0-9.-]+\.)?(" + "|".join(SAFE_GITHUB_DOMAINS) + r")(/.*)?$"
    )
    for url in urls:
        if not domain_pattern.match(url):
            return False

    # Block dangerous destructive HTTP methods if explicitly passed
    if re.search(r"\b-X\s+(DELETE|PUT|PATCH)\b", cmd_str, re.IGNORECASE):
        return False

    return True


def is_safe_git_command(args: list[str]) -> bool:
    """Check if git command is read-only."""
    if len(args) < 2:
        return True  # 'git' alone
    sub = args[1]
    if sub in SAFE_GIT_SUBCOMMANDS:
        return True
    if sub == "config" and len(args) > 2 and args[2] in ("--get", "--list", "-l"):
        return True
    return False


def is_safe_gh_command(cmd_str: str) -> bool:
    """Check if gh command is read-only."""
    trimmed = cmd_str.strip()
    for sub in SAFE_GH_SUBCOMMANDS:
        if trimmed.startswith(f"gh {sub}"):
            return True
    return False


def is_safe_single_command(cmd_str: str) -> bool:
    """Check a single command (without unhandled pipes or chaining)."""
    cmd_str = cmd_str.strip()
    if not cmd_str:
        return True

    # Check standalone exact commands
    if cmd_str in SAFE_STANDALONE_COMMANDS:
        return True

    # Check curl / wget
    if cmd_str.startswith("curl ") or cmd_str == "curl" or cmd_str.startswith("wget ") or cmd_str == "wget":
        return is_safe_curl_or_wget(cmd_str)

    # Check gh
    if cmd_str.startswith("gh "):
        return is_safe_gh_command(cmd_str)

    # Check git
    try:
        tokens = shlex.split(cmd_str)
    except Exception:
        tokens = cmd_str.split()

    if not tokens:
        return True

    base = tokens[0]

    # Handle 'command -v ...'
    if base == "command" and len(tokens) >= 2 and tokens[1] == "-v":
        return True

    if base == "git":
        return is_safe_git_command(tokens)

    # Base safe tools
    if base in SAFE_BASE_COMMANDS:
        # Check that dangerous in-place flags or redirection are not abused if needed
        # (e.g. sed -i is destructive, plain sed / awk is safe)
        if base in ("sed",) and "-i" in tokens:
            return False
        return True

    # Check prefix matching for python/node checks
    for safe_cmd in SAFE_BASE_COMMANDS:
        if cmd_str.startswith(safe_cmd):
            return True

    return False


def is_safe_command_line(cmd_line: str) -> bool:
    """
    Split piped commands (e.g. `curl ... | jq .` or `ls -la | grep foo`)
    and verify every stage in the pipeline is safe.
    """
    cmd_line = cmd_line.strip()
    if not cmd_line:
        return True

    # Disallow dangerous chaining or background subshells if dangerous
    # Split by pipe '|', '&&', ';'
    sub_commands = re.split(r"\||&&|;", cmd_line)
    for sub_cmd in sub_commands:
        sub_cmd = sub_cmd.strip()
        if not sub_cmd:
            continue
        # Strip redirection like > /dev/null, 2>&1
        cleaned_sub = re.sub(r">\s*/dev/null", "", sub_cmd)
        cleaned_sub = re.sub(r"2>&1", "", cleaned_sub)
        cleaned_sub = re.sub(r"2>/dev/null", "", cleaned_sub).strip()

        if not is_safe_single_command(cleaned_sub):
            return False

    return True


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        # Default fallback
        print(json.dumps({"decision": "ask", "reason": "Failed to parse hook payload"}))
        return

    tool_call = payload.get("toolCall", {})
    tool_name = tool_call.get("name", "")
    args = tool_call.get("args", {})

    if tool_name != "run_command":
        # Let default behavior handle non-command tools
        print(json.dumps({}))
        return

    command_line = args.get("CommandLine", "")
    if not command_line:
        print(json.dumps({}))
        return

    if is_safe_command_line(command_line):
        print(json.dumps({
            "decision": "allow",
            "reason": f"Safe command auto-approved: {command_line[:60]}"
        }))
    else:
        print(json.dumps({
            "decision": "ask",
            "reason": f"Command requires confirmation: {command_line[:60]}"
        }))


if __name__ == "__main__":
    main()
