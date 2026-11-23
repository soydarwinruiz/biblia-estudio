"""Diccionario bíblico: lee tools/content/dictionary/*.txt y crea la tabla `dictionary`.
Formato de cada entrada:
    @@ Término | Categoría
    Definición (puede ocupar varios párrafos; las referencias como «Gn. 12:1–3» se vuelven tocables).
"""
import glob, os, re, sqlite3, sys, unicodedata
DB = sys.argv[1]; C = os.path.join(os.path.dirname(os.path.abspath(__file__)), "content", "dictionary")

def fold(s): return "".join(ch for ch in unicodedata.normalize("NFD", s) if unicodedata.category(ch) != "Mn").lower().strip()

entries = {}
for f in sorted(glob.glob(os.path.join(C, "*.txt"))):
    for blk in open(f, encoding="utf-8").read().split("@@ ")[1:]:
        head, body = blk.split("\n", 1)
        term, cat = [x.strip() for x in head.split("|")]
        body = re.sub(r"[ \t]+\n", "\n", body.strip())
        if fold(term) in entries: print("duplicado:", term); continue
        entries[fold(term)] = (term, cat, body)

con = sqlite3.connect(DB)
con.executescript("DROP TABLE IF EXISTS dictionary; CREATE TABLE dictionary(id INTEGER PRIMARY KEY, term TEXT NOT NULL, category TEXT NOT NULL, definition TEXT NOT NULL, norm TEXT NOT NULL, norm_def TEXT NOT NULL); ")
rows = sorted(entries.values(), key=lambda e: fold(e[0]))
con.executemany("INSERT INTO dictionary(term,category,definition,norm,norm_def) VALUES(?,?,?,?,?)",
                [(t, c, d, fold(t), " " + fold(d)) for t, c, d in rows])
con.execute("CREATE INDEX idx_dict_norm ON dictionary(norm)")
con.commit()
from collections import Counter
print("diccionario:", len(rows), dict(Counter(r[1] for r in rows)))
