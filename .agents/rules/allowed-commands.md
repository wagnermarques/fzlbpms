# Safe and Auto-Approved Commands Guideline

The following commands and patterns are considered safe and harmless in this workspace:

1. **GitHub Operations (curl / wget)**:
   - Queries to the GitHub API: `https://api.github.com/...`
   - Fetching raw repository files and artifacts: `https://raw.githubusercontent.com/...`
   - Downloading releases/assets from GitHub: `https://github.com/...`, `https://objects.githubusercontent.com/...`, `https://gist.githubusercontent.com/...`

2. **Read-Only Filesystem & Inspection Commands**:
   - `ls`, `dir`, `tree`, `vdir`
   - `head`, `tail`, `cat`, `less`, `more`, `nl`
   - `grep`, `egrep`, `fgrep`, `rg`, `ag`
   - `find`, `fd`, `locate`, `which`, `whereis`, `type`, `command -v`
   - `pwd`, `stat`, `file`, `wc`, `diff`, `cmp`, `md5sum`, `sha256sum`
   - `jq`, `yq`, `sort`, `uniq`, `cut`, `tr`, `column`, `awk`
   - `echo`, `printf`, `date`, `uptime`, `whoami`, `id`, `uname`, `env`, `printenv`

3. **Version Control & Status Inspection**:
   - `git status`, `git log`, `git diff`, `git show`, `git branch`, `git tag`, `git rev-parse`, `git remote`, `git describe`
   - `gh repo view`, `gh issue list`, `gh issue view`, `gh pr list`, `gh pr view`, `gh run list`, `gh run view`, `gh api`
