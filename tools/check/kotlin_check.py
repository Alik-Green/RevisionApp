#!/usr/bin/env python3
"""Kotlin syntax and import checker for a machine with no JVM.

This project is developed somewhere that has no JDK, no Gradle and no Kotlin
compiler, and cannot reach Maven Central to get one, so the only real build is CI
- a round trip of several minutes. These two scripts are what runs before every
push instead. They are not a compiler and do not pretend to be: they catch the
classes of mistake that are cheap to make and expensive to discover remotely.

    tools/check/lex_kotlin.py <source-dir>...
        A character-level lexer over every .kt file that counts only the brackets
        which are real code - ignoring line comments, nested block comments,
        string and raw-string literals, escapes, character literals and `${...}`
        templates - and reports block comments that close with text on the same
        line. It exists because a KDoc containing a glob like `schema/*.json` once
        terminated its own comment and produced forty fake unresolved-reference
        errors in a neighbouring file.

    tools/check/kotlin_check.py --fix <source-dir>...
        Rewrites every import block into ktlint's order.

        Reports unused imports, imports of com.revisionapp symbols that nothing in
        the module declares, qualified references whose qualifier was never
        imported, and import blocks that are not in ktlint's intellij_idea order. Unused imports and import order are both hard ktlint
        failures, so they are build breakers, not style nits.

Neither knows any Kotlin semantics: a wrong method name, a bad argument type or a
missing opt-in still only shows up in CI.
"""

import pathlib
import re
import sys

CLOSE = {"}": "{", ")": "(", "]": "["}
OPEN = {"{", "(", "["}


def _skip_line_comment(text, index):
    newline = text.find("\n", index)
    return len(text) if newline < 0 else newline


def _skip_block_comment(text, index):
    depth, index, end = 1, index + 2, len(text)
    while index < end and depth:
        if text.startswith("/*", index):
            depth += 1
            index += 2
        elif text.startswith("*/", index):
            depth -= 1
            index += 2
        else:
            index += 1
    return index, depth == 0


def _skip_char_literal(text, index):
    index += 1
    end = len(text)
    while index < end and text[index] != "'":
        index += 2 if text[index] == "\\" else 1
    return index + 1


def scan_brackets(text):
    """Bracket balance, counting only brackets that are code."""
    counts = {"{": 0, "(": 0, "[": 0}
    # Frames: ["string", is_raw] or ["template", brace_depth]. Empty means code.
    stack = []
    index, end = 0, len(text)
    while index < end:
        character = text[index]
        if not stack:
            if text.startswith("//", index):
                index = _skip_line_comment(text, index)
            elif text.startswith("/*", index):
                index, _ = _skip_block_comment(text, index)
            elif text.startswith('"""', index):
                stack.append(["string", True])
                index += 3
            elif character == '"':
                stack.append(["string", False])
                index += 1
            elif character == "'":
                index = _skip_char_literal(text, index)
            elif character in OPEN:
                counts[character] += 1
                index += 1
            elif character in CLOSE:
                counts[CLOSE[character]] -= 1
                index += 1
            else:
                index += 1
            continue

        frame = stack[-1]
        if frame[0] == "template":
            # Inside ${ ... }: this is real code, so brackets count, and the
            # closing brace at depth 1 returns to the surrounding string.
            if character == "{":
                frame[1] += 1
                counts["{"] += 1
                index += 1
            elif character == "}":
                if frame[1] == 1:
                    stack.pop()
                else:
                    frame[1] -= 1
                    counts["{"] -= 1
                index += 1
            elif character in OPEN:
                counts[character] += 1
                index += 1
            elif character in CLOSE:
                counts[CLOSE[character]] -= 1
                index += 1
            elif text.startswith("//", index):
                index = _skip_line_comment(text, index)
            elif text.startswith("/*", index):
                index, _ = _skip_block_comment(text, index)
            elif text.startswith('"""', index):
                stack.append(["string", True])
                index += 3
            elif character == '"':
                stack.append(["string", False])
                index += 1
            elif character == "'":
                index = _skip_char_literal(text, index)
            else:
                index += 1
            continue

        is_raw = frame[1]
        if text.startswith("${", index):
            stack.append(["template", 1])
            index += 2
        elif is_raw and text.startswith('"""', index):
            stack.pop()
            index += 3
        elif not is_raw and character == "\\":
            index += 2
        elif not is_raw and character == '"':
            stack.pop()
            index += 1
        else:
            index += 1
    return counts, stack


def scan_comments(text):
    """Block comments that close with trailing text, or never close at all."""
    problems = []
    index, end, line, depth, start = 0, len(text), 1, 0, 0
    while index < end:
        character = text[index]
        if character == "\n":
            line += 1
            index += 1
            continue
        if depth == 0:
            if text.startswith('"""', index):
                index += 3
                while index < end and not text.startswith('"""', index):
                    if text[index] == "\n":
                        line += 1
                    index += 1
                index += 3
                continue
            if character == '"':
                index += 1
                while index < end and text[index] != '"':
                    index += 2 if text[index] == "\\" else 1
                index += 1
                continue
            if character == "'":
                index = _skip_char_literal(text, index)
                continue
            if text.startswith("//", index):
                index = _skip_line_comment(text, index)
                continue
            if text.startswith("/*", index):
                depth, start, index = 1, line, index + 2
                continue
            index += 1
            continue
        if text.startswith("/*", index):
            depth += 1
            index += 2
            continue
        if text.startswith("*/", index):
            depth -= 1
            index += 2
            if depth == 0:
                tail = text[index:index + 400].split("\n", 1)[0]
                if tail.strip():
                    problems.append((start, line, tail.strip()[:70]))
            continue
        index += 1
    if depth:
        problems.append((start, line, "UNCLOSED"))
    return problems


DECLARATION = re.compile(
    r'(?:^|\n)\s*(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:public |internal |private |protected |expect |actual |abstract |open |sealed '
    r'|data |value |enum |annotation |inline |override |lateinit |const )*'
    r'(?:class|interface|object|fun|val|var|typealias)\s+(?:<[^>]*>\s*)?([A-Za-z_]\w*)'
)

# ktlint's intellij_idea layout: everything else, then java, javax, kotlin, aliases.
GENERATED = {"RevisionDatabase"}

# Types that need no import: kotlin.* is default-imported, and these Compose and
# project names are referenced often enough that flagging them would be noise.
KOTLIN_BUILTINS = {
    "Any", "Boolean", "Byte", "Char", "Comparable", "Double", "Enum", "Float", "Int",
    "Long", "Nothing", "Number", "Short", "String", "Unit", "List", "Map", "Set",
    "ArrayList", "HashMap", "LinkedHashMap", "Pair", "Triple", "Array", "IntArray",
    "ByteArray", "Result", "Math", "Regex", "Throwable", "Exception", "Companion",
    "RevisionDatabase",
    # java.lang is default-imported on every JVM target.
    "System", "Object", "Integer", "Long", "Double", "Float", "Boolean", "Character",
    "Thread", "Runnable", "StringBuilder", "CharSequence", "Iterable", "Override",
    "SuppressWarnings", "Deprecated", "Void", "Class", "Number",
}


def import_group(spec):
    if " as " in spec:
        return 4
    for position, prefix in enumerate(("java.", "javax.", "kotlin."), start=1):
        if spec.startswith(prefix):
            return position
    return 0


def mask_non_code(text):
    """Blank out comments and literal contents, preserving offsets and newlines.

    Order matters: block comments first so a quote inside a comment cannot start a
    phantom string, then strings so a `//` inside a literal (a URL, say) cannot be
    read as a line comment.
    """
    def blank(match):
        return "".join(c if c == "\n" else " " for c in match.group(0))

    masked = re.sub(r"/\*(?:.|\n)*?\*/", blank, text)
    masked = re.sub(r'"""(?:.|\n)*?"""', blank, masked)
    masked = re.sub(r'"(?:\\.|[^"\\])*"', blank, masked)
    masked = re.sub(r"'(?:\\.|[^'\\])*'", blank, masked)
    return re.sub(r"//[^\n]*", blank, masked)


def sort_imports_in(text):
    """The same file with its import block in ktlint's order."""
    lines = text.split("\n")
    indices = [i for i, line in enumerate(lines) if line.startswith("import ")]
    if not indices:
        return text
    specs = sorted(
        (lines[i][len("import "):].strip() for i in indices),
        key=lambda spec: (import_group(spec), spec),
    )
    first, last = indices[0], indices[-1] + 1
    lines[first:last] = ["import " + spec for spec in specs]
    return "\n".join(lines)


def main(argv):
    fix = "--fix" in argv
    roots = [a for a in argv[1:] if not a.startswith("-")] or [
        "composeApp/src", "androidApp/src", "desktopApp/src"]

    if fix:
        changed = 0
        for root in roots:
            for path in sorted(pathlib.Path(root).rglob("*.kt")):
                if "/build/" in str(path).replace("\\", "/"):
                    continue
                text = path.read_text(encoding="utf-8")
                ordered = sort_imports_in(text)
                if ordered != text:
                    path.write_text(ordered, encoding="utf-8")
                    changed += 1
                    print("resorted", path)
        print("{} file(s) resorted".format(changed))
        return 0

    files = [
        path
        for root in roots
        for path in sorted(pathlib.Path(root).rglob("*.kt"))
        if "/build/" not in str(path).replace("\\", "/")
    ]
    if not files:
        print("no Kotlin sources found under " + ", ".join(roots))
        return 2

    declared = set()
    # Names declared in the same package need no import, so each file is checked
    # against the declarations of every file sharing its package.
    package_declarations = {}
    file_package = {}
    for path in files:
        text = path.read_text(encoding="utf-8")
        match = re.search(r"(?:^|\n)package ([\w.]+)", text)
        package = match.group(1) if match else ""
        file_package[path] = package
        bucket = package_declarations.setdefault(package, set())
        for declaration in DECLARATION.finditer(text):
            declared.add(declaration.group(1))
            bucket.add(declaration.group(1))

    problems = 0
    for path in files:
        text = path.read_text(encoding="utf-8")
        counts, open_frames = scan_brackets(text)
        unbalanced = {k: v for k, v in counts.items() if v != 0}
        if unbalanced or open_frames:
            problems += 1
            print("BRACKETS {}: {} open-frames={}".format(
                path, unbalanced, [frame[0] for frame in open_frames]))
        for start, ended, message in scan_comments(text):
            problems += 1
            print("COMMENT {}:{}-{}: {!r}".format(path, start, ended, message))

        lines = text.split("\n")
        indices = {i for i, line in enumerate(lines) if line.startswith("import ")}
        if not indices:
            continue
        body = "\n".join(line for i, line in enumerate(lines) if i not in indices)
        specs = [lines[i][len("import "):].strip() for i in sorted(indices)]
        for spec in specs:
            name = spec.split(" as ")[-1].strip() if " as " in spec else spec.rsplit(".", 1)[-1]
            if not re.search(r"(?<![A-Za-z0-9_])" + re.escape(name) + r"(?![A-Za-z0-9_])", body):
                problems += 1
                print("UNUSED   {}: {}".format(path, spec))
            if spec.startswith("com.revisionapp.") and name not in declared and name not in GENERATED:
                problems += 1
                print("UNRESOLVED {}: {}".format(path, spec))
        # A reference like `Alignment.CenterVertically` with no import of Alignment
        # and no local declaration is a compile error, and nothing else here catches
        # it: unused imports are the opposite mistake. Member accesses (Icons.Filled,
        # Route.Library) are skipped because the qualifier before the dot is what has
        # to resolve, and it is checked on its own iteration.
        simple = {spec.rsplit(".", 1)[-1].split(" as ")[-1].strip() for spec in specs}
        local = set(DECLARATION.findall(text)) | package_declarations.get(file_package[path], set())
        body_text = "\n".join(
            line for line in mask_non_code(text).split("\n") if not line.startswith("import ")
        )
        for match in re.finditer(r"(?<![.\w])([A-Z][A-Za-z0-9_]*)\s*\.", body_text):
            name = match.group(1)
            if name in simple or name in local or name in KOTLIN_BUILTINS:
                continue
            problems += 1
            print("NO IMPORT {}: {} is used qualified but never imported".format(path, name))

        ordered = sorted(specs, key=lambda item: (import_group(item), item))
        if specs != ordered:
            problems += 1
            print("ORDER    {}".format(path))
            for got, want in zip(specs, ordered):
                if got != want:
                    print("         got  {}\n         want {}".format(got, want))
                    break

    print("checked {} files, {} declared names".format(len(files), len(declared)))
    if problems:
        print("{} problem(s)".format(problems))
        return 1
    print("clean")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
