"""Convierte bible.db (Android, FTS4) en la biblioteca para la versión web/iPhone (FTS5)."""
import shutil, sqlite3, sys, gzip, os
src, dst = sys.argv[1], sys.argv[2]
tmp = dst + ".tmp.db"
shutil.copy(src, tmp)
con = sqlite3.connect(tmp)
con.execute("DROP TABLE IF EXISTS verses_fts")
con.execute("CREATE VIRTUAL TABLE verses_fts USING fts5(text, content='verses', content_rowid='id', tokenize=\"unicode61 remove_diacritics 2\")")
con.execute("INSERT INTO verses_fts(verses_fts) VALUES('rebuild')")
con.execute("INSERT INTO verses_fts(verses_fts) VALUES('optimize')")
con.execute("INSERT OR REPLACE INTO meta VALUES('web','1')")
con.commit(); con.execute("VACUUM"); con.close()
with open(tmp, "rb") as f, gzip.open(dst, "wb", compresslevel=6) as g: shutil.copyfileobj(f, g)
print("biblioteca web:", os.path.getsize(tmp) // 1_000_000, "MB ->", os.path.getsize(dst) // 1_000_000, "MB gzip")
os.remove(tmp)
