#!/usr/bin/env python3
"""Move classes to another package and fix every reference to them. Run from the repository root.

    move-classes.py pl.mzebrows.pwt.sim.system.squad TrooperMindSystem TrooperAid ... TrooperMindTest

Each name is a top-level class found by simple name under any module's src/main/java or src/test/java; a
main class goes to the target's main directory and a test to its test directory. Then, across every module:

- an import of the old name becomes an import of the new one, or goes if the file is now in that package;
- a file that shared the old package with a moved class, and names it, gets an import;
- a moved class that names something it left behind in the old package gets an import.

"Names" counts code and javadoc links ({@link}, {@linkplain}, @see), never other comment text or strings.
A class that is not public and is named across the new line only in javadoc has its links turned into
{@code} rather than imported - a link to what cannot be reached fails Error Prone. Otherwise it does not
touch visibility: what was package-private and is now used across packages fails to compile, and that is
the list of what must become public - members included, constants as well as methods. Spotless orders the
imports afterwards."""

import pathlib, re, sys

MODULES = [p for p in pathlib.Path(".").iterdir() if (p / "src").is_dir()]
JAVA_ROOTS = [r for m in MODULES for r in (m / "src/main/java", m / "src/test/java") if r.is_dir()]


def package_of(text):
    m = re.search(r"^package\s+([\w.]+);", text, re.M)
    return m.group(1) if m else ""


def strip(src):
    """Code only: comments, strings and chars removed - but javadoc links kept, as words."""
    links = " ".join(re.findall(r"(?:\{@link(?:plain)?|@see)\s+([\w.#]+)", src))
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif src[i] in "\"'":
            q, j = src[i], i + 1
            while j < n and src[j] != q:
                j += 2 if src[j] == "\\" else 1
            i = j + 1
        else:
            out.append(src[i])
            i += 1
    return "".join(out) + "\n" + links.replace("#", " ").replace(".", " ")


def names(text):
    body = re.sub(r"^(package|import)\s.*$", "", text, flags=re.M)
    return set(re.findall(r"\b[A-Z]\w*\b", strip(body)))


def in_code(text, name):
    """Named in code, not only in a javadoc link."""
    body = re.sub(r"^(package|import)\s.*$", "", text, flags=re.M)
    return re.search(r"\b" + name + r"\b", strip(body).rsplit("\n", 1)[0]) is not None


PUBLIC = {}


def public(name):
    """Whether the top-level class of that simple name is declared public."""
    if name not in PUBLIC:
        PUBLIC[name] = False
        for root in JAVA_ROOTS:
            for p in root.rglob(name + ".java"):
                PUBLIC[name] = re.search(r"^public\s", p.read_text(encoding="utf-8"), re.M) is not None
    return PUBLIC[name]


def member(rest):
    """The #member a link names after its class, if any; its label goes."""
    return rest.split()[0] if rest.startswith("#") else ""


def refer(text, name, fqn):
    """Makes the file able to name the class: an import, or - for a class it cannot reach and only links to
    in javadoc - a {@code} in place of the link."""
    if not public(name) and not in_code(text, name):
        return re.sub(r"\{@link(?:plain)?\s+" + name + r"(?=[\s}#])([^}]*)\}",
                      lambda m: "{@code " + name + member(m.group(1)) + "}", text)
    return add_import(text, fqn)


def add_import(text, fqn):
    line = f"import {fqn};\n"
    if line in text:
        return text
    m = list(re.finditer(r"^import\s[^;]+;\n", text, re.M))
    at = m[-1].end() if m else re.search(r"^package\s[^;]+;\n", text, re.M).end()
    return text[:at] + ("" if m else "\n") + line + text[at:]


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    target, wanted = sys.argv[1], sys.argv[2:]
    files = {}
    for root in JAVA_ROOTS:
        for p in root.rglob("*.java"):
            if p.stem in wanted:
                if p.stem in files:
                    sys.exit(f"{p.stem} is in two places: {files[p.stem][0]} and {p}")
                files[p.stem] = (p, root)
    missing = sorted(set(wanted) - set(files))
    if missing:
        sys.exit(f"not found: {missing}")

    moved = {}  # simple name -> (old fqn, new fqn, old package)
    for name, (path, root) in files.items():
        text = path.read_text(encoding="utf-8")
        old = package_of(text)
        moved[name] = (f"{old}.{name}", f"{target}.{name}", old)
    old_packages = {old for _, _, old in moved.values()}

    # What each old package holds once the moves are done, so a moved class can import what it left behind.
    left = {}
    for root in JAVA_ROOTS:
        for p in root.rglob("*.java"):
            if p.stem in moved or p.stem == "package-info":
                continue
            pkg = package_of(p.read_text(encoding="utf-8"))
            if pkg in old_packages:
                left[p.stem] = f"{pkg}.{p.stem}"

    for name, (path, root) in files.items():
        dest = root / target.replace(".", "/") / path.name
        dest.parent.mkdir(parents=True, exist_ok=True)
        text = path.read_text(encoding="utf-8")
        text = re.sub(r"^package\s+[\w.]+;", f"package {target};", text, count=1, flags=re.M)
        used = names(text)
        for other, fqn in sorted(left.items()):
            if other in used and other != name:
                text = refer(text, other, fqn)
        dest.write_text(text, encoding="utf-8")
        path.unlink()
        files[name] = (dest, root)

    for root in JAVA_ROOTS:
        for p in root.rglob("*.java"):
            text = p.read_text(encoding="utf-8")
            original = text
            pkg = package_of(text)
            for name, (old_fqn, new_fqn, old_pkg) in moved.items():
                if pkg == target:
                    text = text.replace(f"import {old_fqn};\n", "")
                else:
                    text = text.replace(f"import {old_fqn};\n", f"import {new_fqn};\n")
                if pkg == old_pkg and pkg != target and p.stem not in moved and name in names(text):
                    text = refer(text, name, new_fqn)
            if text != original:
                p.write_text(text, encoding="utf-8")
    for name, (path, _) in sorted(files.items()):
        print(f"{moved[name][0]} -> {moved[name][1]}  ({path})")


main()
