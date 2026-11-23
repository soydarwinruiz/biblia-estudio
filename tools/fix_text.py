"""Une palabras partidas por la extracción del PDF ("Hum anidad" -> "Humanidad")."""
import re, sqlite3, sys
from collections import Counter

W = re.compile(r"[A-Za-zÁÉÍÓÚÑÜáéíóúñü]+")

def build_vocab(con):
    c = Counter()
    for (t,) in con.execute("SELECT text FROM verses"):
        c.update(w.lower() for w in W.findall(t))
    for tbl, col in (("study_notes", "text"), ("study_intros", "content"), ("articles", "content")):
        for (t,) in con.execute(f"SELECT {col} FROM {tbl}"):
            c.update(w.lower() for w in W.findall(t))
    return c

def fixer(vocab):
    def ok(w): return vocab.get(w.lower(), 0) >= 3
    pat = re.compile(r"\b([A-Za-zÁÉÍÓÚÑÜáéíóúñü]+) ([a-záéíóúñü]+)\b")
    def fix(text, stats):
        def rep(m):
            a, b = m.group(1), m.group(2)
            joined = a + b
            if vocab.get(joined.lower(), 0) >= 3 and (not ok(a) or not ok(b)) and vocab[joined.lower()] > 2 * min(vocab.get(a.lower(), 0), vocab.get(b.lower(), 0)):
                stats[f"{a} {b}"] += 1
                return joined
            return m.group(0)
        prev = None
        while prev != text:
            prev = text; text = pat.sub(rep, text)
        return text
    return fix

def main(db):
    con = sqlite3.connect(db)
    vocab = build_vocab(con)
    fix = fixer(vocab); stats = Counter()
    for tbl, key, col in (("study_notes", "id", "text"), ("articles", "id", "content"), ("study_intros", "rowid", "content"), ("topic_entries", "rowid", "text")):
        rows = con.execute(f"SELECT {key}, {col} FROM {tbl}").fetchall()
        upd = [(fix(t, stats), k) for k, t in rows]
        con.executemany(f"UPDATE {tbl} SET {col}=? WHERE {key}=?", [(t, k) for (t, k), (_, o) in zip(upd, rows) if t != o])
    con.commit()
    print("uniones:", sum(stats.values()), "distintas:", len(stats))
    print(stats.most_common(40))

if __name__ == "__main__":
    main(sys.argv[1])
