"""Contenido diario, cuestionarios, planes de lectura, guías y búsqueda de texto completo."""
import re, sqlite3, sys, random, json, os
from books import BOOKS, USFM_TO_ID
DB = sys.argv[1]; C = os.path.join(os.path.dirname(os.path.abspath(__file__)), "content")
con = sqlite3.connect(DB)
NAMES = {i + 1: b[1] for i, b in enumerate(BOOKS)}

def parse_ref(s):
    """'JHN 3:16-18' o 'ISA 52:13-53:12' -> (book, ch, v, ch_end, v_end)"""
    s = s.strip(); usfm, rest = s.split(" ", 1); b = USFM_TO_ID[usfm]
    m = re.fullmatch(r"(\d+)(?::(\d+))?(?:-(\d+)(?::(\d+))?)?", rest)
    ch = int(m.group(1)); v = int(m.group(2) or 0)
    if m.group(4): ce, ve = int(m.group(3)), int(m.group(4))
    elif m.group(3):
        if m.group(2): ce, ve = ch, int(m.group(3))
        else: ce, ve = int(m.group(3)), 0
    else: ce, ve = ch, v
    return b, ch, v, ce, ve

def text_of(b, ch, v, ce, ve, version="RVR1960"):
    rows = con.execute("SELECT chapter,verse,text FROM verses WHERE version=? AND book=? AND (chapter>? OR (chapter=? AND verse>=?)) AND (chapter<? OR (chapter=? AND verse<=?)) ORDER BY chapter,verse",
                       (version, b, ch, ch, v, ce, ce, ve if ve else 999)).fetchall()
    return " ".join(r[2] for r in rows)

def ref_label(b, ch, v, ce, ve):
    s = f"{NAMES[b]} {ch}"
    if v: s += f":{v}"
    if ce != ch: s += f"–{ce}" + (f":{ve}" if ve else "")
    elif ve and ve != v: s += f"–{ve}"
    return s

con.executescript("""
DROP TABLE IF EXISTS daily_verses; DROP TABLE IF EXISTS daily_passages; DROP TABLE IF EXISTS prayers;
DROP TABLE IF EXISTS lessons; DROP TABLE IF EXISTS quiz; DROP TABLE IF EXISTS plans; DROP TABLE IF EXISTS plan_days;
DROP TABLE IF EXISTS book_abbr; DROP TABLE IF EXISTS verses_fts; DROP TABLE IF EXISTS meta;
CREATE TABLE daily_verses(id INTEGER PRIMARY KEY, book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, verse_end INTEGER NOT NULL);
CREATE TABLE daily_passages(id INTEGER PRIMARY KEY, title TEXT NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, chapter_end INTEGER NOT NULL, verse_end INTEGER NOT NULL);
CREATE TABLE prayers(id INTEGER PRIMARY KEY, title TEXT NOT NULL, text TEXT NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, chapter_end INTEGER NOT NULL, verse_end INTEGER NOT NULL);
CREATE TABLE lessons(id INTEGER PRIMARY KEY, title TEXT NOT NULL, kind TEXT NOT NULL, book INTEGER NOT NULL, content TEXT NOT NULL, questions TEXT NOT NULL, ref TEXT NOT NULL);
CREATE TABLE quiz(id INTEGER PRIMARY KEY, question TEXT NOT NULL, options TEXT NOT NULL, answer INTEGER NOT NULL, ref TEXT NOT NULL, category TEXT NOT NULL, difficulty INTEGER NOT NULL);
CREATE TABLE plans(id INTEGER PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL, days INTEGER NOT NULL);
CREATE TABLE plan_days(plan INTEGER NOT NULL, day INTEGER NOT NULL, readings TEXT NOT NULL, PRIMARY KEY(plan, day));
CREATE TABLE book_abbr(abbr TEXT PRIMARY KEY, book INTEGER NOT NULL);
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
""")

# ---- abreviaturas ----
ab = {}
for i, b in enumerate(BOOKS):
    for a in b[5] + [b[1], b[2], b[0]]:
        ab[a.lower().replace(".", "")] = i + 1
con.executemany("INSERT OR REPLACE INTO book_abbr VALUES(?,?)", ab.items())

# ---- versículos del día ----
vod = [parse_ref(l) for l in open(f"{C}/verses_of_day.txt", encoding="utf-8") if l.strip() and not l.startswith("#")]
random.seed(7); random.shuffle(vod)
for r in vod: assert text_of(*r), r
con.executemany("INSERT INTO daily_verses(book,chapter,verse,verse_end) VALUES(?,?,?,?)", [(b, c, v, ve) for b, c, v, ce, ve in vod])

# ---- pasajes del día ----
pas = []
for l in open(f"{C}/passages.txt", encoding="utf-8"):
    if not l.strip() or l.startswith("#"): continue
    ref, title = [x.strip() for x in l.split("|")]
    r = parse_ref(ref); assert text_of(*r), ref; pas.append((title,) + r)
random.seed(11); random.shuffle(pas)
con.executemany("INSERT INTO daily_passages(title,book,chapter,verse,chapter_end,verse_end) VALUES(?,?,?,?,?,?)", pas)

# ---- oraciones ----
raw = open(f"{C}/prayers.txt", encoding="utf-8").read().split("\n---\n")
prs = []
for blk in raw:
    lines = [x for x in blk.strip().split("\n") if x.strip()]
    title, ref = [x.strip() for x in lines[0].split("|")]
    prs.append((title, " ".join(lines[1:]).strip()) + parse_ref(ref))
con.executemany("INSERT INTO prayers(title,text,book,chapter,verse,chapter_end,verse_end) VALUES(?,?,?,?,?,?,?)", prs)

# ---- guías (artículos de método) ----
guides = open(f"{C}/guides.md", encoding="utf-8").read().split("@@ ")[1:]
next_id = con.execute("SELECT COALESCE(MAX(id),-1)+1 FROM articles").fetchone()[0]
gids = []
for g in guides:
    head, body = g.split("\n", 1); cat, title = [x.strip() for x in head.split("|")]
    con.execute("INSERT INTO articles(id,category,title,content) VALUES(?,?,?,?)", (next_id, cat, title, body.strip()))
    gids.append(next_id); next_id += 1

# ---- lecciones ----
Q = {
 "Temas históricos y teológicos": ["¿Qué revela este libro acerca del carácter de Dios?", "¿Cuál de estos temas necesita usted aplicar esta semana?", "¿Cómo apunta este libro hacia Cristo y su obra?"],
 "Retos de interpretación": ["¿Qué principio de interpretación ayuda a resolver estas dificultades?", "¿Por qué es importante el contexto para entender estos pasajes?", "¿Qué otras Escrituras arrojan luz sobre estos temas?"],
 "Contexto histórico": ["¿Cómo cambia su lectura del libro conocer este contexto?", "¿Qué paralelos hay entre aquella situación y la nuestra?", "¿Qué hizo Dios en medio de esas circunstancias?"],
 "Autor y fecha": ["¿Qué sabemos del autor y cómo influye en su mensaje?", "¿A quién se dirigía originalmente el libro?", "¿Qué le enseña esto sobre la inspiración de las Escrituras?"],
 "Título": ["¿Qué indica el título acerca del contenido del libro?", "¿Qué palabras clave del libro descubre en su introducción?"],
}
DOCQ = ["¿Qué verdad central enseña esta lección?", "¿Qué textos bíblicos la respaldan?", "¿Cómo debe cambiar esta verdad su manera de vivir hoy?"]
first_passage = {}
for t, b, c, v, ce, ve in pas:
    first_passage.setdefault(b, ref_label(b, c, v, ce, ve))
book_lessons = []; seen = set()
for sec in ["Temas históricos y teológicos", "Contexto histórico", "Retos de interpretación", "Autor y fecha"]:
    pass
for b in range(1, 67):
    for sec in ["Temas históricos y teológicos", "Contexto histórico", "Retos de interpretación", "Autor y fecha"]:
        r = con.execute("SELECT content FROM study_intros WHERE book=? AND section=?", (b, sec)).fetchone()
        if not r or len(r[0]) < 200 or r[0] in seen: continue
        seen.add(r[0])
        bname = NAMES[b] if not (b in (11, 12) and r[0]) else "1 y 2 Reyes"
        if b in (9, 10): bname = "1 y 2 Samuel"
        if b in (13, 14) and con.execute("SELECT COUNT(*) FROM study_intros WHERE book=14 AND section=?", (sec,)).fetchone()[0] and b == 13:
            bname = "1 y 2 Crónicas"
        book_lessons.append((f"{bname}: {sec.lower().capitalize()}", "libro", b, r[0], json.dumps(Q[sec], ensure_ascii=False), first_passage.get(b, f"{NAMES[b]} 1")))
doc_lessons = []
for aid, cat, title, content in con.execute("SELECT id,category,title,content FROM articles ORDER BY id").fetchall():
    if len(content) < 400: continue
    # dividir artículos largos en partes de ~3500 caracteres
    paras = content.split("\n\n"); parts = []; cur = ""
    for p in paras:
        if len(cur) + len(p) > 3500 and cur: parts.append(cur); cur = p
        else: cur = (cur + "\n\n" + p) if cur else p
    if cur: parts.append(cur)
    for n, ptxt in enumerate(parts):
        t = title if len(parts) == 1 else f"{title} ({n + 1}/{len(parts)})"
        doc_lessons.append((t, "doctrina" if aid not in gids else "método", 0, ptxt, json.dumps(DOCQ, ensure_ascii=False), ""))
# semana: 6 lecciones de libros + 1 de doctrina/método
lessons = []; di = 0
for i, l in enumerate(book_lessons):
    lessons.append(l)
    if i % 6 == 5 and di < len(doc_lessons): lessons.append(doc_lessons[di]); di += 1
lessons += doc_lessons[di:]
con.executemany("INSERT INTO lessons(title,kind,book,content,questions,ref) VALUES(?,?,?,?,?,?)", lessons)

# ---- cuestionario ----
quiz = []
def addq(q, correct, wrong, ref, cat, diff):
    opts = [correct] + list(wrong); random.shuffle(opts)
    quiz.append((q, json.dumps(opts, ensure_ascii=False), opts.index(correct), ref, cat, diff))
random.seed(42)
for l in open(f"{C}/quiz.txt", encoding="utf-8"):
    if not l.strip() or l.startswith("#"): continue
    p = [x.strip() for x in l.split("|")]
    ref = p[5]
    try:
        rr = parse_ref(ref) if re.fullmatch(r"\w+ \d+(:\d+)?(-\d+(:\d+)?)?", ref) else None
    except Exception: rr = None
    label = ref_label(*rr) if rr else ref
    addq(p[0], p[1], p[2:5], label, "General", int(p[6]))
AUTH = {"GEN":"Moisés","EXO":"Moisés","LEV":"Moisés","NUM":"Moisés","DEU":"Moisés","JOS":"Josué","PSA":"David y otros","PRO":"Salomón y otros",
 "ECC":"Salomón","SNG":"Salomón","ISA":"Isaías","JER":"Jeremías","LAM":"Jeremías","EZK":"Ezequiel","DAN":"Daniel","HOS":"Oseas","JOL":"Joel",
 "AMO":"Amós","OBA":"Abdías","JON":"Jonás","MIC":"Miqueas","NAM":"Nahúm","HAB":"Habacuc","ZEP":"Sofonías","HAG":"Hageo","ZEC":"Zacarías",
 "MAL":"Malaquías","EZR":"Esdras","NEH":"Nehemías","MAT":"Mateo","MRK":"Marcos","LUK":"Lucas","JHN":"Juan","ACT":"Lucas","ROM":"Pablo",
 "1CO":"Pablo","2CO":"Pablo","GAL":"Pablo","EPH":"Pablo","PHP":"Pablo","COL":"Pablo","1TH":"Pablo","2TH":"Pablo","1TI":"Pablo","2TI":"Pablo",
 "TIT":"Pablo","PHM":"Pablo","JAS":"Santiago","1PE":"Pedro","2PE":"Pedro","1JN":"Juan","2JN":"Juan","3JN":"Juan","JUD":"Judas","REV":"Juan"}
people = sorted(set(AUTH.values()))
for usfm, a in AUTH.items():
    b = USFM_TO_ID[usfm]
    addq(f"Según la introducción de MacArthur, ¿quién escribió {NAMES[b]}?", a, random.sample([p for p in people if p != a], 3), f"Introducción a {NAMES[b]}", "Autores", 2)
for i, bk in enumerate(BOOKS):
    b = i + 1; n = bk[4]
    wrong = set()
    while len(wrong) < 3:
        w = max(1, n + random.choice([-3, -2, -1, 1, 2, 3, 5, -5, 10]))
        if w != n: wrong.add(w)
    addq(f"¿Cuántos capítulos tiene {bk[1]}?", str(n), [str(x) for x in wrong], f"{bk[1]}", "Libros", 3 if n > 5 else 2)
    addq(f"¿A qué testamento pertenece {bk[1]}?", "Antiguo Testamento" if bk[3] == "AT" else "Nuevo Testamento",
         ["Nuevo Testamento" if bk[3] == "AT" else "Antiguo Testamento", "Libros apócrifos", "No es un libro bíblico"], bk[1], "Libros", 1)
    if b < 66:
        nxt = BOOKS[b][1]
        pool = [x[1] for x in BOOKS if x[1] not in (nxt, bk[1])]
        addq(f"¿Qué libro viene inmediatamente después de {bk[1]}?", nxt, random.sample(pool, 3), f"{nxt}", "Orden de los libros", 2)
WRE = r"\b[A-Za-zÁÉÍÓÚÑáéíóúñ]{5,}\b"
WORDPOOL = sorted({x for r in vod for x in re.findall(WRE, text_of(*r))})
STOP = set("porque cuando entonces también delante contra sobre todos todas aquellos aquel estos estas para entre desde hasta como pero sino".split())
for (b, c, v, ce, ve) in vod:
    t = text_of(b, c, v, ce, ve); label = ref_label(b, c, v, ce, ve)
    same = [i + 1 for i, x in enumerate(BOOKS) if x[3] == BOOKS[b - 1][3] and i + 1 != b]
    short = t if len(t) < 230 else t[:227] + "…"
    addq(f"¿En qué libro se encuentra?\n«{short}»", NAMES[b], [NAMES[x] for x in random.sample(same, 3)], label, "¿Dónde dice?", 2)
    words = [w for w in re.findall(r"\b[A-Za-zÁÉÍÓÚÑáéíóúñ]{6,}\b", t) if w.lower() not in STOP]
    if words and len(t) < 260:
        w = random.choice(words)
        blank = re.sub(r"\b" + re.escape(w) + r"\b", "_____", t, count=1)
        WRE = r"\b[A-Za-zÁÉÍÓÚÑáéíóúñ]{6,}\b"
        cands = [x for x in WORDPOOL if x.lower() != w.lower() and abs(len(x) - len(w)) <= 2 and x.lower() not in STOP]
        same_case = [x for x in cands if x[0].isupper() == w[0].isupper()]
        pool = same_case if len(same_case) >= 3 else cands
        wr = random.sample(pool, 3)
        wr = [x[0].upper() + x[1:] if w[0].isupper() else (x if x.lower() in ("jehová", "dios", "señor", "cristo", "jesús", "espíritu", "israel", "jerusalén") else x.lower()) for x in wr]
        addq(f"Completa el versículo ({label}):\n«{blank}»", w, wr, label, "Completa el versículo", 1 if len(t) < 120 else 2)
con.executemany("INSERT INTO quiz(question,options,answer,ref,category,difficulty) VALUES(?,?,?,?,?,?)", quiz)

# ---- planes de lectura ----
counts = {(b, c): n for b, c, n in con.execute("SELECT book,chapter,COUNT(*) FROM verses WHERE version='RVR1960' GROUP BY book,chapter")}
def chapters(books):
    return [(b, c) for b in books for c in range(1, BOOKS[b - 1][4] + 1)]
def split(chs, days, weight=True):
    if len(chs) < days:
        return [[chs[k] for k in range(len(chs)) if (k * days) // len(chs) == d] for d in range(days)]
    w = [counts.get(x, 20) if weight else 1 for x in chs]; total = sum(w); out = []; i = 0
    for d in range(days):
        target = total * (d + 1) / days; day = []
        acc = sum(w[:i])
        while i < len(chs) and (acc + w[i] / 2 <= target or not day) and len(chs) - i > days - d - 1:
            day.append(chs[i]); acc += w[i]; i += 1
        if d == days - 1: day += chs[i:]; i = len(chs)
        out.append(day)
    return out
def enc(day):
    # agrupar capítulos consecutivos del mismo libro
    res = []
    for b, c in day:
        if res and res[-1][0] == b and res[-1][2] == c - 1: res[-1][2] = c
        else: res.append([b, c, c])
    return ";".join(f"{b}:{s}-{e}" for b, s, e in res)
OT = list(range(1, 40)); NT = list(range(40, 67))
plans = []
plans.append(("La Biblia en un año", "Toda la Biblia en orden canónico, unos 3 a 4 capítulos por día.", split(chapters(range(1, 67)), 365)))
ot = split(chapters([b for b in OT if b not in (19, 20)]), 365); nt = split(chapters(NT), 365); ps = split(chapters([19, 20]), 365)
plans.append(("Un año: AT, NT y Salmos", "Cada día una porción del Antiguo Testamento, una del Nuevo y una de Salmos o Proverbios.", [a + b + c for a, b, c in zip(ot, nt, ps)]))
plans.append(("Nuevo Testamento en 90 días", "Los 27 libros del Nuevo Testamento en tres meses.", split(chapters(NT), 90)))
plans.append(("Los Evangelios en 40 días", "La vida de Jesús según Mateo, Marcos, Lucas y Juan.", split(chapters([40, 41, 42, 43]), 40)))
plans.append(("Salmos en 30 días", "Cinco salmos por día durante un mes.", split(chapters([19]), 30, weight=False)))
plans.append(("Proverbios en 31 días", "Un capítulo de sabiduría para cada día del mes.", split(chapters([20]), 31, weight=False)))
plans.append(("Cartas de Pablo en 30 días", "De Romanos a Filemón.", split(chapters(range(45, 58)), 30)))
plans.append(("Pentateuco en 50 días", "Los cinco libros de Moisés.", split(chapters(range(1, 6)), 50)))
plans.append(("Profetas en 60 días", "De Isaías a Malaquías.", split(chapters(range(23, 40)), 60)))
for pid, (name, desc, days) in enumerate(plans, 1):
    con.execute("INSERT INTO plans VALUES(?,?,?,?)", (pid, name, desc, len(days)))
    con.executemany("INSERT INTO plan_days VALUES(?,?,?)", [(pid, d + 1, enc(x)) for d, x in enumerate(days)])
    assert sum(len(x) for x in days) == len(set(c for x in days for c in x))

# ---- búsqueda de texto completo ----
con.execute("CREATE VIRTUAL TABLE verses_fts USING fts4(content='verses', text, tokenize=unicode61 'remove_diacritics=1')")
con.execute("INSERT INTO verses_fts(verses_fts) VALUES('rebuild')")
con.execute("INSERT INTO verses_fts(verses_fts) VALUES('optimize')")
con.execute("INSERT INTO meta VALUES('version','1')")
con.commit()
print("vod", len(vod), "pasajes", len(pas), "oraciones", len(prs), "lecciones", len(lessons), "quiz", len(quiz), "planes", len(plans))
con.execute("VACUUM"); con.close()
