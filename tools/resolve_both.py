"""Merge կոնֆլիկտներում պահում է ԵՐԿՈՒ կողմն էլ (մերը, հետո upstream-ինը)՝ strings.xml-ի համար,
որտեղ երկուսն էլ ֆայլի վերջում նոր տողեր են ավելացրել։

  python tools/resolve_both.py <file> [<file> ...]
"""
import re
import sys

for path in sys.argv[1:]:
    src = open(path, encoding="utf-8").read()
    pattern = re.compile(r"<<<<<<< [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n", re.S)
    out, n = pattern.subn(lambda m: m.group(1) + m.group(2), src)
    open(path, "w", encoding="utf-8").write(out)
    print(path, "hunks:", n)
