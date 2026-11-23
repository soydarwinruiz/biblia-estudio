"""Importa las versiones de bible-data-es-spa a SQLite (tablas versions, books, verses, headings, crossrefs)."""
import json, glob, os, re, sqlite3, sys, html
from html.parser import HTMLParser
from books import BOOKS, USFM_TO_ID

SRC = sys.argv[1]; DB = sys.argv[2]
ORDER = ["RVR1960","NBLA","LBLA","NVI","NTV","RVA2015","RVC","DHH94I","DHHS94","TLA","TLAI","NVI-ES"]

def ws(s): return re.sub(r"\s+", " ", s).strip()

def create(con):
    con.executescript("""
    CREATE TABLE IF NOT EXISTS versions(id TEXT PRIMARY KEY, name TEXT NOT NULL, copyright TEXT, ord INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS books(id INTEGER PRIMARY KEY, usfm TEXT NOT NULL, name TEXT NOT NULL, abbr TEXT NOT NULL, testament TEXT NOT NULL, chapters INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS verses(id INTEGER PRIMARY KEY, version TEXT NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, verse_end INTEGER NOT NULL, para INTEGER NOT NULL, text TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS headings(version TEXT NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL, before_verse INTEGER NOT NULL, kind TEXT NOT NULL, text TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS crossrefs(book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, refs TEXT NOT NULL);
    """)

class NoteParser(HTMLParser):
    """Extrae las notas de referencia cruzada (class 'note x') del HTML de RVR1960."""
    def __init__(self):
        super().__init__(); self.stack=[]; self.cur=None; self.in_note=0; self.in_body=False; self.buf=""; self.out={}
    def handle_starttag(self, tag, attrs):
        a=dict(attrs); cls=(a.get("class") or "").split()
        if tag=="span" and "verse" in cls and a.get("data-usfm"):
            self.cur=a["data-usfm"]
        if self.in_note: self.in_note+=1
        if tag=="span" and "note" in cls and "x" in cls: self.in_note=1; self.buf=""
        elif self.in_note and "body" in cls: self.in_body=True
    def handle_endtag(self, tag):
        if self.in_note:
            self.in_note-=1
            if self.in_note==0:
                if self.cur and self.buf.strip():
                    self.out.setdefault(self.cur, []).append(ws(self.buf))
                self.in_body=False
    def handle_data(self, d):
        if self.in_note and self.in_body: self.buf+=d

def main():
    if os.path.exists(DB): os.remove(DB)
    con=sqlite3.connect(DB); create(con)
    for i,b in enumerate(BOOKS):
        con.execute("INSERT INTO books VALUES(?,?,?,?,?,?)",(i+1,b[0],b[1],b[2],b[3],b[4]))
    files=glob.glob(os.path.join(SRC,"data/*/*.json"))
    for f in files:
        d=json.load(open(f,encoding="utf-8"))
        vid=d["local_abbreviation"]
        if "spa_es" in f: vid="NVI-ES"; name="Nueva Versión Internacional (España)"
        else: name=d["local_title"]
        con.execute("INSERT INTO versions VALUES(?,?,?,?)",(vid,name,d["copyright"]["text"],ORDER.index(vid)))
        rows=[]; heads=[]
        for b in d["books"]:
            bid=USFM_TO_ID.get(b["usfm"])
            if not bid: continue
            for ch in b["chapters"]:
                try: cn=int(ch["usfm"].split(".")[1])
                except: continue
                acc={}  # verse -> [text, end, para]
                order=[]; pending=[]
                for it in ch["items"]:
                    t=it["type"]; txt=ws(" ".join(it["lines"]))
                    if t=="verse":
                        nums=it["verse_numbers"]
                        if not nums: continue
                        v=nums[0]
                        if v not in acc:
                            acc[v]=[txt,max(nums),1 if it.get("np") else 0]; order.append(v)
                            for kind,ht in pending: heads.append((vid,bid,cn,v,kind,ht))
                            pending=[]
                        else:
                            acc[v][0]=ws(acc[v][0]+" "+txt); acc[v][1]=max(acc[v][1],max(nums))
                    elif txt:
                        kind={"label":"label","section1":"section","section2":"section"}.get(t,"heading")
                        if t=="section2" and re.match(r"^\(.*\)$",txt): continue
                        pending.append((kind,txt))
                for v in order:
                    if acc[v][0]:
                        rows.append((vid,bid,cn,v,acc[v][1],acc[v][2],acc[v][0]))
        con.executemany("INSERT INTO verses(version,book,chapter,verse,verse_end,para,text) VALUES(?,?,?,?,?,?,?)",rows)
        con.executemany("INSERT INTO headings VALUES(?,?,?,?,?,?)",heads)
        print(vid,len(rows),len(heads))
        if vid=="RVR1960":
            n=0
            for b in d["books"]:
                for ch in b["chapters"]:
                    p=NoteParser(); p.feed(ch["html"])
                    for usfm,refs in p.out.items():
                        parts=usfm.split(".")
                        if len(parts)<3 or parts[0] not in USFM_TO_ID: continue
                        con.execute("INSERT INTO crossrefs VALUES(?,?,?,?)",(USFM_TO_ID[parts[0]],int(parts[1]),int(parts[2])," ".join(refs)))
                        n+=1
            print("crossrefs",n)
    con.execute("CREATE INDEX idx_verses ON verses(version,book,chapter,verse)")
    con.execute("CREATE INDEX idx_head ON headings(version,book,chapter)")
    con.execute("CREATE INDEX idx_xref ON crossrefs(book,chapter,verse)")
    con.commit(); con.close()
main()
