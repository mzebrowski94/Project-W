#!/usr/bin/env python3
"""Who uses whom under one package root, from source with comments and strings stripped. Run from the
repository root.

    system-graph.py [--root DIR]                every class: what it uses, who uses it, what it writes
    system-graph.py [--root DIR] --split FILE   test a split: what must be public, what crosses, where
                                                each test goes

DIR is the package root to read: sim.system's by default, the client's with
`--root core/src/main/java/pl/mzebrows/pwt/client`.

A split file has one line per fenced package, `name: Class Class ...`; `#` starts a comment and a line
ending in `\\` continues. Every class under the root must be named exactly once. The root's own name
(`client`) is a line like any other - the classes left directly in it.

Source rather than bytecode on purpose: javac inlines a compile-time constant into its user, so the
bytecode loses exactly the references a split must keep - `Trooper.Mode`-style nested names and `static
final` numbers. A name that only appears in a comment is not a use, which is why comments are stripped.
What a system writes is read from the Written by column of docs/components.md, which ADR 0019's test keeps
true."""

import collections, pathlib, re, sys

ROOT = pathlib.Path("sim/src/main/java/pl/mzebrows/pwt/sim/system")
MODULES = ("map", "sim", "core", "ui", "net", "lwjgl3")
DOC = pathlib.Path("docs/components.md")
# What a class touches of libGDX, by the import that brings it in: the running application (Gdx), drawing
# state (GL), a device it reads (device), and libGDX's key and button tables (keys - Input.Keys names, no
# device). The rest of libGDX - Color, math, collections, the Screen interface - is plain library.
GDX = [
    ("Gdx", r"com\.badlogic\.gdx\.Gdx;"),
    ("GL", r"com\.badlogic\.gdx\.(graphics\.(g2d|glutils|GL\d+|Texture|Pixmap|Camera|OrthographicCamera|Mesh)"
           r"|utils\.viewport\.|utils\.ScreenUtils|assets\.|scenes\.)"),
    ("device", r"com\.badlogic\.gdx\.(InputAdapter|InputProcessor|controllers\.)|org\.lwjgl\."),
    ("keys", r"com\.badlogic\.gdx\.Input;"),
]


def package(path):
    """A source file's package less the pl.mzebrows.pwt prefix, from where it sits under src/*/java."""
    parts = path.parts
    at = max(i for i, part in enumerate(parts) if part == "java")
    return ".".join(parts[at + 4:-1])


def test_root(root):
    return pathlib.Path(str(root).replace("/src/main/java/", "/src/test/java/"))


def outside_files(root):
    """Every main source file in the repository that is not under the root, grouped by its package."""
    groups = collections.defaultdict(list)
    for module in MODULES:
        for p in sorted(pathlib.Path(module, "src/main/java").rglob("*.java")):
            if root not in p.parents:
                groups[package(p) or module].append(p)
    return groups


def code(path):
    """The source with comments, string and char literals removed."""
    src = path.read_text(encoding="utf-8")
    src = re.sub(r'"""(.*?)"""', '""', src, flags=re.S)
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif c in "\"'":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            out.append(c + c)
            i = j + 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def mentions(text, names):
    """The names used in code - not in an import, which a javadoc link alone may have brought in. A name after
    a dot (`GraphicConfig.Hud`) is a member, and a type the file declares inside itself shadows the
    top-level one of the same name, so neither is a use of that class."""
    body = re.sub(r"^(package|import)\s.*$", "", text, flags=re.M)
    words = set(re.findall(r"(?<![.\w])[A-Z]\w*\b", body))
    declared = re.findall(r"\b(?:class|record|enum|interface)\s+([A-Z]\w*)", body)
    return sorted((words & names) - set(declared[1:]))


def imported(text, classes, home=None):
    """What a file outside a class's package can name: what it imports, by name or by its package's star.
    A file in the same package as the class (a test beside it) names it without an import."""
    used = []
    for u in mentions(text, set(classes)):
        pkg = "pl.mzebrows.pwt." + classes[u]["pkg"]
        if home == classes[u]["pkg"] or re.search(r"^import\s+" + re.escape(pkg) + r"\.(" + u + r"|\*);", text, re.M):
            used.append(u)
    return used


def touches(path):
    """What of libGDX a file imports, by GDX's kinds."""
    text = path.read_text(encoding="utf-8")
    return sorted(kind for kind, pattern in GDX if re.search(r"^import\s+(static\s+)?(" + pattern + ")", text, re.M))


def load(root):
    classes = {}
    for p in sorted(root.rglob("*.java")):
        if p.stem == "package-info":
            continue
        text = code(p)
        public = re.search(r"^public\s+(final\s+|abstract\s+)?(class|interface|enum|record|@interface)\s+"
                           + p.stem + r"\b", text, re.M) is not None
        classes[p.stem] = {"pkg": package(p), "public": public, "text": text, "gdx": touches(p)}
    names = set(classes)
    for name, c in classes.items():
        c["uses"] = [u for u in mentions(c["text"], names) if u != name]
    for c in classes.values():
        c["used_by"] = []
    for name, c in classes.items():
        for u in c["uses"]:
            classes[u]["used_by"].append(name)
    writes = collections.defaultdict(list)
    column = None
    for line in DOC.read_text(encoding="utf-8").splitlines():
        parts = [p.strip() for p in line.split("|")]
        if "Written by" in parts:
            column = parts.index("Written by")
            continue
        m = re.fullmatch(r"\*\*(\w+)\*\*", parts[1]) if len(parts) > 2 else None
        if column is not None and m:
            for writer in re.findall(r"`(\w+)`", parts[column]):
                writes[writer].append(m.group(1))
    for name, c in classes.items():
        c["writes"] = sorted(writes.get(name, []))
    outside = collections.defaultdict(lambda: collections.defaultdict(set))
    for where, paths in outside_files(root).items():
        for p in paths:
            for u in imported(code(p), classes):
                outside[u][where].add(p.stem)
    tests = {}
    for p in sorted(test_root(root).rglob("*.java")):
        pkg = package(p)
        tests[p.stem] = {"pkg": pkg, "text": code(p), "uses": imported(code(p), classes, pkg)}
    names = set(tests)
    for name, t in tests.items():
        t["fixtures"] = [u for u in mentions(t["text"], names) if u != name and not u.endswith("Test")]
    return classes, outside, tests


def describe(classes, outside):
    for name in sorted(classes, key=lambda n: (classes[n]["pkg"], n)):
        c = classes[name]
        kind = "system" if name.endswith("System") else "helper"
        print(f"{name}  [{c['pkg']}, {kind}, {'public' if c['public'] else 'package-private'}]")
        print(f"  uses     {', '.join(c['uses']) or '-'}")
        if c["gdx"]:
            print(f"  touches  {', '.join(c['gdx'])}")
        print(f"  used by  {', '.join(sorted(c['used_by'])) or '-'}")
        if c["writes"]:
            print(f"  writes   {', '.join(c['writes'])}")
        for where, users in sorted(outside.get(name, {}).items()):
            print(f"  from {where}: {', '.join(sorted(users))}")


def read_split(path):
    split, owner = {}, {}
    text = re.sub(r"\\\n", " ", path.read_text(encoding="utf-8"))
    for line in text.splitlines():
        line = line.split("#")[0].strip()
        if not line:
            continue
        pkg, _, members = line.partition(":")
        split[pkg.strip()] = members.split()
        for m in members.split():
            if m in owner:
                sys.exit(f"{m} is in both {owner[m]} and {pkg.strip()}")
            owner[m] = pkg.strip()
    return split, owner


def evaluate(classes, outside, tests, split, owner, root):
    missing = sorted(set(classes) - set(owner))
    unknown = sorted(set(owner) - set(classes))
    if missing or unknown:
        sys.exit(f"not placed: {missing}\nnot a class: {unknown}")
    print("== Packages")
    for pkg, members in split.items():
        systems = [m for m in members if m.endswith("System")]
        print(f"{pkg}: {len(systems)} systems, {len(members) - len(systems)} helpers")
    print("\n== Must be public (used from another fenced package; systems are public anyway)")
    print("   system: built by Simulation; entry: used by the split's root package alone; port: by a sibling")
    ports = collections.defaultdict(dict)
    for name, c in classes.items():
        foreign = sorted(u for u in c["used_by"] if owner[u] != owner[name])
        if foreign:
            ports[owner[name]][name] = foreign
    # A class only the root's own classes use is an entry point - public as a system is, because the root
    # builds and drives it - and not a port: no sibling package leans on it.
    counts = collections.Counter()
    for pkg in split:
        for name, users in sorted(ports[pkg].items()):
            if name.endswith("System"):
                kind = "system"
            elif pkg != root and all(owner[u] == root for u in users):
                kind = "entry"
            else:
                kind = "port"
            counts[kind] += 1
            print(f"  {pkg}.{name} ({kind}) <- " + ", ".join(f"{u} ({owner[u]})" for u in users))
    print("  total: " + ", ".join(f"{n} {k}" for k, n in sorted(counts.items())))
    print("\n== Public for outside the root only")
    for name in sorted(outside):
        if not name.endswith("System") and owner[name] != "query":
            print(f"  {owner[name]}.{name} <- " + "; ".join(
                f"{w}: {', '.join(sorted(u))}" for w, u in sorted(outside[name].items())))
    print("\n== Package -> package uses (excluding query)")
    edges = collections.defaultdict(set)
    for name, c in classes.items():
        for u in c["uses"]:
            if owner[u] != owner[name] and owner[u] != "query":
                edges[(owner[name], owner[u])].add(f"{name}->{u}")
    for (a, b), why in sorted(edges.items()):
        print(f"  {a} -> {b}: {', '.join(sorted(why))}")
    print("\n== query writes (must be empty)")
    for name in split.get("query", []):
        if classes[name]["writes"]:
            print(f"  {name}: {', '.join(classes[name]['writes'])}")
    print("\n== Placement rule: where the components each system writes live")
    # A component lives where most of its other writers are; one nobody else writes lives with its writer.
    writers = collections.defaultdict(list)
    for name, c in classes.items():
        for comp in c["writes"]:
            writers[comp].append(name)
    for name in sorted(classes, key=lambda n: (owner[n], n)):
        c = classes[name]
        if not c["writes"]:
            continue
        tally = collections.Counter()
        for comp in c["writes"]:
            others = collections.Counter(owner[w] for w in writers[comp] if w != name)
            if not others:
                tally[owner[name]] += 1
                continue
            top = max(others.values())
            for pkg, n in others.items():
                if n == top:
                    tally[pkg] += 1 / sum(1 for v in others.values() if v == top)
        best = max(tally.values())
        flag = "" if tally[owner[name]] == best else "   <- rule says " + "/".join(
            sorted(p for p, n in tally.items() if n == best))
        print(f"  {owner[name]}.{name}: " + ", ".join(
            f"{p} {n:g}" for p, n in tally.most_common()) + flag)
    print("\n== libGDX touched, by package (Gdx = the running application, GL, device, keys = Input.Keys)")
    for pkg, members in split.items():
        touched = [f"{m} ({'/'.join(classes[m]['gdx'])})" for m in members if classes[m]["gdx"]]
        print(f"  {pkg}: {', '.join(touched) or '-'}")
    print("\n== Tests (private = package-private once split: no port, no user outside the root, no system)")
    shown = {t for t in tests if t.endswith("Test")}
    for test in sorted(shown):
        t = tests[test]
        private = sorted({owner[u] for u in t["uses"]
                          if u not in outside and u not in ports[owner[u]] and not u.endswith("System")})
        homes = sorted({owner[u] for u in t["uses"]})
        hard = "  <- package-private in two packages" if len(private) > 1 else ""
        print(f"  {test}: private reach {private or '-'}; uses {homes or '-'}{hard}")
    fixtures = collections.defaultdict(list)
    for test in sorted(shown):
        for f in tests[test]["fixtures"]:
            fixtures[f].append(test)
    for f, users in sorted(fixtures.items()):
        print(f"  fixture {f} <- {', '.join(users)}")


def main():
    args = sys.argv[1:]
    root = ROOT
    if args[:1] == ["--root"] and len(args) >= 2:
        root, args = pathlib.Path(args[1]), args[2:]
    if not root.is_dir():
        sys.exit(f"no such root: {root}")
    classes, outside, tests = load(root)
    if len(args) == 2 and args[0] == "--split":
        split, owner = read_split(pathlib.Path(args[1]))
        evaluate(classes, outside, tests, split, owner, package(root / "x.java").split(".")[-1])
    elif not args:
        describe(classes, outside)
    else:
        sys.exit(__doc__)


main()
