#!/usr/bin/env python3
"""Who uses whom in sim.system and sim.upgrade, from source with comments and strings stripped. Run from the
repository root.

    system-graph.py                  every class: what it uses, who uses it, what it writes
    system-graph.py --split FILE     test a split: what must be public, what crosses, where each test goes

A split file has one line per fenced package, `name: Class Class ...`; `#` starts a comment and a line
ending in `\\` continues. Every class in sim.system and sim.upgrade must be named exactly once.

Source rather than bytecode on purpose: javac inlines a compile-time constant into its user, so the
bytecode loses exactly the references a split must keep - `Trooper.Mode`-style nested names and `static
final` numbers. A name that only appears in a comment is not a use, which is why comments are stripped.
What a system writes is read from the Written by column of docs/components.md, which ADR 0019's test keeps
true."""

import collections, pathlib, re, sys

SIM = pathlib.Path("sim/src/main/java/pl/mzebrows/pwt/sim")
TESTS = pathlib.Path("sim/src/test/java/pl/mzebrows/pwt/sim")
OUTSIDE = {
    "sim root": [p for p in SIM.glob("*.java")],
    "sim.scenario": list((SIM / "scenario").rglob("*.java")),
    "sim.collision": list((SIM / "collision").rglob("*.java")),
    "sim.def": list((SIM / "def").rglob("*.java")),
    "sim.component": list((SIM / "component").rglob("*.java")),
    "core": list(pathlib.Path("core/src/main/java").rglob("*.java")),
    "ui": list(pathlib.Path("ui/src/main/java").rglob("*.java")),
    "net": list(pathlib.Path("net/src/main/java").rglob("*.java")),
}
DOC = pathlib.Path("docs/components.md")


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
    words = set(re.findall(r"\b[A-Z]\w*\b", text))
    return sorted(words & names)


def imported(text, classes, home=None):
    """What a file outside a class's package can name: what it imports, by name or by its package's star.
    A file in the same package as the class (a test beside it) names it without an import."""
    used = []
    for u in mentions(text, set(classes)):
        pkg = "pl.mzebrows.pwt." + classes[u]["pkg"]
        if home == classes[u]["pkg"] or re.search(r"^import\s+" + re.escape(pkg) + r"\.(" + u + r"|\*);", text, re.M):
            used.append(u)
    return used


def load():
    classes = {}
    for pkg in ("system", "upgrade"):
        for p in sorted((SIM / pkg).rglob("*.java")):
            if p.stem == "package-info":
                continue
            text = code(p)
            public = re.search(r"^public\s+(final\s+|abstract\s+)?(class|interface|enum|record|@interface)\s+"
                               + p.stem + r"\b", text, re.M) is not None
            classes[p.stem] = {"pkg": "sim." + pkg, "public": public, "text": text}
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
    for where, paths in OUTSIDE.items():
        for p in paths:
            for u in imported(code(p), classes):
                outside[u][where].add(p.stem)
    tests = {}
    for p in sorted(TESTS.rglob("*.java")):
        rel = p.relative_to(TESTS)
        pkg = "sim." + ".".join(rel.parts[:-1]) if len(rel.parts) > 1 else "sim"
        tests[p.stem] = {"pkg": pkg, "uses": imported(code(p), classes, pkg)}
    return classes, outside, tests


def describe(classes, outside):
    for name in sorted(classes, key=lambda n: (classes[n]["pkg"], n)):
        c = classes[name]
        kind = "system" if name.endswith("System") else "helper"
        print(f"{name}  [{c['pkg']}, {kind}, {'public' if c['public'] else 'package-private'}]")
        print(f"  uses     {', '.join(c['uses']) or '-'}")
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


def evaluate(classes, outside, tests, split, owner):
    missing = sorted(set(classes) - set(owner))
    unknown = sorted(set(owner) - set(classes))
    if missing or unknown:
        sys.exit(f"not placed: {missing}\nnot a class: {unknown}")
    print("== Packages")
    for pkg, members in split.items():
        systems = [m for m in members if m.endswith("System")]
        print(f"{pkg}: {len(systems)} systems, {len(members) - len(systems)} helpers")
    print("\n== Must be public (used from another fenced package; systems are public anyway)")
    ports = collections.defaultdict(dict)
    for name, c in classes.items():
        foreign = sorted(u for u in c["used_by"] if owner[u] != owner[name])
        if foreign:
            ports[owner[name]][name] = foreign
    for pkg in split:
        for name, users in sorted(ports[pkg].items()):
            kind = "system" if name.endswith("System") else "port"
            print(f"  {pkg}.{name} ({kind}) <- " + ", ".join(f"{u} ({owner[u]})" for u in users))
    print("\n== Public today only for outside sim.system/sim.upgrade")
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
    print("\n== Tests")
    for test, t in sorted(tests.items()):
        if not t["pkg"].startswith(("sim.system", "sim.upgrade")):
            continue
        private = sorted({owner[u] for u in t["uses"]
                          if not classes[u]["public"] and u not in ports[owner[u]]})
        homes = sorted({owner[u] for u in t["uses"]})
        hard = "  <- package-private in two packages" if len(private) > 1 else ""
        print(f"  {test}: private reach {private or '-'}; uses {homes or '-'}{hard}")


def main():
    classes, outside, tests = load()
    if len(sys.argv) == 3 and sys.argv[1] == "--split":
        split, owner = read_split(pathlib.Path(sys.argv[2]))
        evaluate(classes, outside, tests, split, owner)
    else:
        describe(classes, outside)


main()
