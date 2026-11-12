"""PreToolUse guard for Bash: what Claude may not do to git, whatever the command's wording.

Permission rules match a command's text, so they cannot tell the main checkout from a worktree, or a
worktree path written relatively from one written absolutely. This reads the command, works out which
checkout each git call lands in (`-C`, a leading `cd`, or the session's cwd) and refuses:

- anywhere: push; deleting a branch with -D; force-removing a worktree; dropping or clearing the stash
  (shared by every worktree); pointing git at other hooks; unsetting CLAUDECODE, which the main guard
  (.githooks/reference-transaction) keys on;
- in the main checkout, where Mateusz's uncommitted work lives: anything that discards working-tree
  changes or switches its branch - reset --hard/--merge/--keep, checkout, switch, restore of the
  working tree (docs/ and .scratch/ excepted, the documented recovery), clean -f.

Moving main itself is the reference-transaction hook's job, not this one's. Everything else passes to
the permission rules. docs/workflow.md, *AFK runs*.
"""

import json
import os
import re
import shlex
import subprocess
import sys

SEPARATORS = {"&&", "||", ";", "|", "|&", "&", "\n"}
RESTORE_OK = ("docs/", ".scratch/")
CHANGE_DIR = {"cd", "chdir", "pushd", "set-location", "sl", "push-location"}
CONFIG_READS = {"--get", "--get-all", "--get-regexp", "get"}
# Changing CLAUDECODE, not reading it: unset, env -u, an assignment in either shell, Remove-Item Env:.
TOUCHES_CLAUDECODE = re.compile(
    r"\bunset\b[^;&|\n]*\bCLAUDECODE\b|-u\s*CLAUDECODE\b|--unset[= ]CLAUDECODE\b"
    r"|(?<![{$:])\bCLAUDECODE\s*=|\$env:CLAUDECODE\s*=|Remove-Item\b[^;&|\n]*Env:\\?CLAUDECODE\b"
    r"|SetEnvironmentVariable\(\s*['\"]CLAUDECODE", re.IGNORECASE)


def deny(reason):
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": "git-guard: " + reason}}))
    sys.exit(0)


def native(path):
    """A Git Bash path (/d/x) as Windows sees it (D:/x); anything else unchanged."""
    m = re.match(r"^/([a-zA-Z])(/|$)(.*)", path)
    if m and os.name == "nt":
        return m.group(1).upper() + ":/" + m.group(3)
    return path


def resolve(base, path):
    path = native(os.path.expanduser(path))
    return os.path.normpath(path if os.path.isabs(path) else os.path.join(base, path))


def is_main_checkout(directory):
    """True when directory is inside the repository's main checkout, not a linked worktree. Unknown is True."""
    try:
        out = subprocess.run(["git", "-C", directory, "rev-parse", "--git-dir", "--git-common-dir"],
                             capture_output=True, text=True, timeout=5)
    except (OSError, subprocess.SubprocessError):
        return True
    if out.returncode != 0:
        return False  # not a repository at all: nothing of Mateusz's to lose
    lines = out.stdout.split()
    if len(lines) < 2:
        return True
    git_dir, common = (os.path.normpath(os.path.join(directory, p)) for p in lines[:2])
    return os.path.normcase(git_dir) == os.path.normcase(common)


def segments(command, powershell):
    lexer = shlex.shlex(command, posix=True, punctuation_chars="&|;")
    lexer.whitespace_split = True
    lexer.commenters = ""
    if powershell:
        lexer.escape = ""  # a backslash is a Windows path separator there, not an escape
    words = []
    try:
        for token in lexer:
            if token in SEPARATORS:
                if words:
                    yield words
                words = []
            else:
                words.append(token)
    except ValueError:
        deny("could not parse the command; write the git call on its own")
    if words:
        yield words


def check_git(args, directory):
    """args: everything after `git`. directory: where the call runs before any -C."""
    i = 0
    while i < len(args) and args[i].startswith("-"):
        flag = args[i]
        if flag == "-C" and i + 1 < len(args):
            directory = resolve(directory, args[i + 1])
            i += 2
        elif flag == "-c" and i + 1 < len(args):
            if args[i + 1].lower().startswith("core.hookspath"):
                deny("git -c core.hooksPath is off limits")
            i += 2
        elif flag in ("--git-dir", "--work-tree") or flag.startswith(("--git-dir=", "--work-tree=")):
            deny("--git-dir/--work-tree hide which checkout a call lands in; use -C")
        else:
            i += 1
    if i >= len(args):
        return
    sub, rest = args[i], args[i + 1:]

    if sub == "push":
        deny("Claude does not push; Mateusz does")
    short_d = any(a.startswith("-") and not a.startswith("--") and "D" in a for a in rest)
    long_d = any(a in ("-d", "--delete") for a in rest) and any(a in ("-f", "--force") for a in rest)
    if sub == "branch" and (short_d or long_d):
        deny("branch -D deletes unmerged work; use -d, which refuses it")
    if sub == "worktree" and rest[:1] == ["remove"] and any(a in ("-f", "--force") for a in rest):
        deny("worktree remove --force discards uncommitted work")
    if sub == "stash" and rest[:1] and rest[0] in ("drop", "clear", "pop"):
        deny("the stash is shared by every worktree; use a WIP commit on your own branch")
    if (sub == "config" and any(a.lower().startswith("core.hookspath") for a in rest)
            and not any(a in CONFIG_READS for a in rest)):
        deny("core.hooksPath is off limits")

    if not is_main_checkout(directory):
        return
    where = "in the main checkout (" + directory + "), where Mateusz's work lives - "
    if sub == "reset" and any(a in ("--hard", "--merge", "--keep") for a in rest):
        deny(where + "reset that discards changes is for worktrees only")
    if sub in ("checkout", "switch"):
        deny(where + sub + " would change his checkout; work in a worktree under .claude/worktrees/")
    if sub == "restore":
        staged_only = ("--staged" in rest or "-S" in rest) and "--worktree" not in rest and "-W" not in rest
        paths = [a for a in rest if not a.startswith("-")]
        if not staged_only and not (paths and all(p.replace("\\", "/").startswith(RESTORE_OK) for p in paths)):
            deny(where + "restore of the working tree is limited to docs/ and .scratch/")
    if sub == "clean" and any(a.startswith("-") and "f" in a for a in rest):
        deny(where + "clean -f deletes untracked files")


def main():
    event = json.load(sys.stdin)
    command = (event.get("tool_input") or {}).get("command") or ""
    lowered = command.lower()
    if "git" not in lowered and "claudecode" not in lowered:
        return
    if TOUCHES_CLAUDECODE.search(command):
        deny("CLAUDECODE is what keeps the main guard on; leave it alone")
    directory = native(event.get("cwd") or os.getcwd())
    for words in segments(command, event.get("tool_name") == "PowerShell"):
        while words and re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", words[0]):
            words = words[1:]  # VAR=value prefixes
        if not words:
            continue
        program = os.path.basename(words[0]).lower()  # Windows resolves GIT and Git to git.exe
        if program in CHANGE_DIR:
            target = [w for w in words[1:] if not w.startswith("-")]
            if target:
                directory = resolve(directory, target[0])
        elif program in ("git", "git.exe"):
            check_git(words[1:], directory)


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as error:  # a guard that crashes must refuse, not let the command through
        deny("guard failed (" + type(error).__name__ + "); git commands cannot be checked")
