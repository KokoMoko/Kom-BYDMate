"""Kotlin ֆայլերում ստուգում է (){}[] հավասարակշռությունը՝ string-երից (${...}-ով հանդերձ) և
մեկնաբանություններից դուրս։  python tools/check_brackets.py <file.kt> [...]"""
import sys


def strip(src: str) -> str:
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i)
            i = n if j < 0 else j + 2
            continue
        if src.startswith('"""', i):
            i = src.find('"""', i + 3) + 3
            continue
        c = src[i]
        if c == '"':
            i = skip_string(src, i + 1)
            continue
        if c == "'":
            j = i + 1
            if j < n and src[j] == "\\":
                j += 2
            else:
                j += 1
            if j < n and src[j] == "'":
                i = j + 1
                continue
        out.append(c)
        i += 1
    return "".join(out)


def skip_string(src: str, i: int) -> int:
    """i՝ բացող չակերտից հետո․ վերադարձնում է փակող չակերտից հետո ինդեքսը։"""
    n = len(src)
    while i < n:
        c = src[i]
        if c == "\\":
            i += 2
            continue
        if src.startswith("${", i):
            i = skip_template(src, i + 2)
            continue
        if c == '"':
            return i + 1
        i += 1
    return n


def skip_template(src: str, i: int) -> int:
    depth, n = 1, len(src)
    while i < n and depth:
        c = src[i]
        if c == '"':
            i = skip_string(src, i + 1)
            continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        i += 1
    return i


bad = 0
for f in sys.argv[1:]:
    code = strip(open(f, encoding="utf-8").read())
    cnt = {c: code.count(c) for c in "(){}[]"}
    ok = cnt["("] == cnt[")"] and cnt["{"] == cnt["}"] and cnt["["] == cnt["]"]
    bad += not ok
    print("OK " if ok else "BAD", f.replace("\\", "/").split("/")[-1], cnt)
sys.exit(1 if bad else 0)
