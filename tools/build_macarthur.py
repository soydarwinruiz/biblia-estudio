"""Extrae de la Biblia de Estudio MacArthur (texto de pdftotext) las introducciones, notas por versículo,
artículos, índice temático y estudio de teología, y los guarda en SQLite."""
import re, sqlite3, sys, json
from books import BOOKS, USFM_TO_ID

TXT = sys.argv[1]; DB = sys.argv[2]
RAW = [l.replace('\f', '').rstrip() for l in open(TXT, encoding='utf-8').read().split('\n')]

GLOSS = {"cp. compare","Cp. compare","ca. cerca de, aproximadamente","Ca. cerca de, aproximadamente","m metro",
 "km kilómetro","Lit. literalmente","lit. literalmente","S Sur","N Norte","O Oeste","E Este","contra. contraste",
 "M Texto mayoritario","TR Texto Recibido","LXX Septuaginta; una traducción antigua del Antiguo Testamento en griego",
 "Descargado de http://www.yahwehjireh.org","\\"}
GLOSS_RE = re.compile(r"^(LXX|TR|TM|M|MS|MSS|p\. ej\.|i\.e\.|gr\.|heb\.|aram\.|ss\.|Mss\.|NVI|RVR|vv?\.|caps?\.|a\.C\.|d\.C\.) [a-záéíóúñ;,]")

def is_noise(s):
    s = s.strip()
    if s in GLOSS: return True
    if re.fullmatch(r"\d{1,4}", s): return True
    return False

LINES = [("" if is_noise(l) else l.strip()) for l in RAW]   # índices conservados (0-based)

# ---------- utilidades de párrafo ----------
def paragraphs(lines):
    """Une líneas en párrafos. Rompe en línea vacía solo si la línea anterior terminó la oración."""
    out = []; cur = ""
    for l in lines:
        if not l:
            continue
        if cur and cur_end_break(cur_last[0]) :
            out.append(cur); cur = l
        else:
            cur = (cur + " " + l) if cur else l
        cur_last[0] = l
    if cur: out.append(cur)
    return [fix(p) for p in out]
cur_last = [""]
def cur_end_break(prev):
    return bool(prev) and len(prev) < 68 and re.search(r"[.:!?”)»]$", prev) is not None
def fix(p):
    p = re.sub(r"\s+", " ", p)
    p = re.sub(r"(\w)- (\w)", r"\1\2", p) if False else p
    return p.strip()

# ---------- localizar libros ----------
starts = []
for i in range(3000, len(LINES) - 1):
    if LINES[i + 1] in ("Título", "Introducción") and LINES[i] and (LINES[i].isupper() or LINES[i] in ("Jesucristo", "de los apóstoles")):
        starts.append(i + 1)  # índice de la línea 'Título'
apx = next(i for i in range(136000, len(LINES)) if LINES[i] == "APÉNDICES")
assert len(starts) == 65, len(starts)
groups = []
k = 0
order = [b[0] for b in BOOKS]
for s in starts:
    if order[k] == "1KI": groups.append((s, ["1KI", "2KI"])); k += 2
    else: groups.append((s, [order[k]])); k += 1
assert k == 66

HEADINGS = ["Título","Autor y fecha","Contexto histórico","Contexto histórico y teológico","Personas destacadas",
 "Temas históricos y teológicos","Temas cristológicos","Palabras clave","Retos de interpretación","Bosquejo",
 "Introducción","Bosquejo y cronología","Bosquejo biográfico de Job","Contexto e historia","Bosquejo."]

MULTI = re.compile(r"^(\d{1,3}):(\d{1,3})((?:\s?[–-]\s?\d{1,3}(?::\d{1,3})?)?(?:,\s?\d{1,3}(?:[–-]\d{1,3})?)*)\s+(\S.*)$")
SINGLE = re.compile(r"^(\d{1,2})((?:[–-]\d{1,2})?(?:,\s?\d{1,2}(?:[–-]\d{1,2})?)*)\s+(\S.*)$")
OK_FIRST = re.compile(r"^[A-Za-zÁÉÍÓÚÑáéíóúñ¿¡“\"«(…]")

def parse_range(ch, v, tail):
    tail = tail.replace(" ", "")
    ce, ve = ch, v
    m = re.match(r"^[–-](\d+):(\d+)", tail)
    if m: ce, ve = int(m.group(1)), int(m.group(2))
    else:
        m = re.match(r"^[–-](\d+)", tail)
        if m: ve = int(m.group(1))
    nums = re.findall(r"\d+", tail)
    if "," in tail and nums and not re.match(r"^[–-]\d+:", tail):
        ve = max(ve, int(nums[-1]))
    return ce, ve

intros = []   # (book, ord, section, text)
notes = []    # (book, ch, v, ce, ve, text)

def chain(cands, nbooks):
    """Subsecuencia no decreciente más larga de claves (libro, cap, vers); cada candidato puede pertenecer a cualquiera de los libros del grupo."""
    n = len(cands); best = [1] * (n * nbooks); prev = [-1] * (n * nbooks)
    keys = [[(bk, c[1], c[2]) for bk in range(nbooks)] for c in cands]
    for i in range(n):
        for bi in range(nbooks):
            if bi > 0 and cands[i][1] > BOOKS[order.index(cands[i][6][bi])][4]: best[i*nbooks+bi] = -10**6; continue
            ki = keys[i][bi]
            for j in range(max(0, i - 400), i):
                for bj in range(nbooks):
                    kj = keys[j][bj]
                    if kj <= ki and best[j*nbooks+bj] + 1 > best[i*nbooks+bi] and (ki[0] == kj[0] or ki[1] == 1):
                        best[i*nbooks+bi] = best[j*nbooks+bj] + 1; prev[i*nbooks+bi] = j*nbooks+bj
    k = max(range(n * nbooks), key=lambda x: best[x]); out = []
    while k >= 0: out.append((k // nbooks, k % nbooks)); k = prev[k]
    return out[::-1]

for gi, (s, books) in enumerate(groups):
    e = (groups[gi + 1][0] - 3) if gi + 1 < len(groups) else apx
    maxch = max(BOOKS[order.index(b)][4] for b in books)
    single = maxch == 1
    bos = next((i for i in range(s, e) if LINES[i].startswith("Bosquejo")), s)
    cands = []
    for i in range(bos, e):
        l = LINES[i]
        if not l: continue
        if single:
            mm = SINGLE.match(l)
            if mm and OK_FIRST.match(mm.group(3)):
                v = int(mm.group(1))
                if v <= 60: cands.append((i, 1, v, 1, parse_range(1, v, mm.group(2))[1], mm.group(3), books))
        else:
            mm = MULTI.match(l)
            if mm and OK_FIRST.match(mm.group(4)):
                ch, v = int(mm.group(1)), int(mm.group(2))
                if ch <= maxch:
                    ce, ve = parse_range(ch, v, mm.group(3))
                    cands.append((i, ch, v, ce, ve, mm.group(4), books))
    sel = chain(cands, len(books)) if cands else []
    first = cands[sel[0][0]][0] if sel else e
    intro_lines = LINES[s:first]
    for n, (ci, bi) in enumerate(sel):
        c = cands[ci]
        endl = cands[sel[n + 1][0]][0] if n + 1 < len(sel) else e
        body = [c[5]]; short_run = []
        for l in LINES[c[0] + 1:endl]:
            if not l: continue
            if len(l) < 32 and not re.search(r"[.!?”)]$", l):
                short_run.append(l); continue
            if short_run and len(short_run) < 3: body.extend(short_run)
            short_run = []; body.append(l)
        if short_run and len(short_run) < 3: body.extend(short_run)
        notes.append((USFM_TO_ID[books[bi]], c[1], c[2], c[3], c[4], " ".join(body)))
    # secciones de la introducción
    sec = "Introducción"; buf = []
    secs = []
    for l in intro_lines:
        if l in HEADINGS:
            if buf: secs.append((sec, buf))
            sec = l.rstrip("."); buf = []
        else: buf.append(l)
    if buf: secs.append((sec, buf))
    for b in books:
        for n, (sname, bl) in enumerate(secs):
            if sname.startswith("Bosquejo"):
                txt = "\n".join(x for x in bl if x)
                txt = re.sub(r"\n(?![IVXLC]+\.|[A-Z]\.|\d+\.)", " ", txt)
            else:
                cur_last[0] = ""
                txt = "\n\n".join(paragraphs(bl))
            if txt.strip():
                intros.append((USFM_TO_ID[b], n, sname, txt.strip()))

# limpiar texto de notas
clean_notes = []
for (b, ch, v, ce, ve, t) in notes:
    t = re.sub(r"\s+", " ", t).strip()
    clean_notes.append((b, ch, v, ce, ve, t))

# ---------- artículos ----------
def L1(n): return n - 1   # número de línea (1-based) -> índice
def find(text, a=0, b=None):
    for i in range(a, b or len(LINES)):
        if LINES[i] == text: return i
    raise ValueError(text)
def block(a, b):
    cur_last[0] = ""
    return "\n\n".join(paragraphs(LINES[a:b]))

articles = []  # (category, ord, title, content)
def add_article(cat, title, a, b):
    articles.append((cat, len(articles), title, block(a, b)))
i_obt = find("LA BIBLIA", 880); i_est = find("LA BIBLIA", i_obt + 1)
i_esp = find("EDICIÓN EN ESPAÑOL", i_est); i_reina = find("LA “REINA” DE LAS VERSIONES EN CASTELLANO", i_esp)
i_abr = find("ABREVIATURAS", i_reina)
add_article("Introducción a la Biblia", "Cómo obtuvimos la Biblia", i_obt + 1, i_est - 1)
add_article("Introducción a la Biblia", "Cómo estudiar la Biblia", i_est + 1, i_esp - 2)
add_article("Introducción a la Biblia", "La “Reina” de las versiones en castellano", i_reina + 1, i_abr - 3)
i_pent = find("PENTATEUCO", 2700); i_harm = find("SAMUEL, REYES Y CRÓNICAS", i_pent)
add_article("Antiguo Testamento", "Introducción al Pentateuco", i_pent + 1, i_harm - 2)
i_prof = find("PROFETAS", i_harm); i_gen = starts[0] - 2
add_article("Antiguo Testamento", "Introducción a los profetas", i_prof + 1, i_gen)
i_inter = find("PERÍODO INTERTESTAMENTARIO", 74000); i_nt = find("NUEVO TESTAMENTO", i_inter)
add_article("Período intertestamentario", "Introducción al período intertestamentario", i_inter + 1, i_nt)
i_ev = find("EVANGELIOS", i_nt); i_ev2 = find("EVANGELIOS", i_ev + 1)
add_article("Nuevo Testamento", "Introducción a los Evangelios", i_ev + 1, i_ev2 - 5)
i_fe = find("VERDADERA FE SALVADORA", 167000); i_teo = find("TEOLOGÍA", i_fe); i_tab = find("TABLAS DE PESOS, MEDIDAS Y MONEDAS", i_teo)
add_article("Doctrina", "Características de la verdadera fe salvadora", i_fe + 1, i_teo - 2)
# Teología por secciones
secs = [i for i in range(i_teo + 1, i_tab) if LINES[i] and LINES[i].isupper() and len(re.sub(r"[^A-ZÁÉÍÓÚÑ]", "", LINES[i])) >= 4 and not re.search(r"\d", LINES[i])]
for n, a in enumerate(secs):
    b = secs[n + 1] if n + 1 < len(secs) else i_tab
    articles.append(("Estudio panorámico de teología", len(articles), LINES[a].capitalize(), block(a + 1, b)))

# ---------- índice temático ----------
i_idx = find("ÍNDICE TEMÁTICO", 137880); i_plan = find("Lea la Biblia en un año", i_idx)
topics = []; entries = []
cur_t = None
for l in LINES[i_idx + 1:i_plan]:
    if not l: continue
    letters = re.sub(r"[^A-Za-zÁÉÍÓÚÑÜáéíóúñü]", "", l)
    if letters and letters.isupper() and len(letters) >= 2 and not re.search(r"\d", l):
        cur_t = len(topics); topics.append(l.strip()); continue
    if cur_t is None: continue
    # continuación de una línea con referencias
    if entries and entries[-1][0] == cur_t and (re.match(r"^[\d:;,–\- ]+", l) or re.match(r"^(\d )?[A-ZÁÉ][a-záéíóú]{0,4}\. \d", l)) and not entries[-1][2].endswith("."):
        entries[-1][2] += " " + l; continue
    if entries and entries[-1][0] == cur_t and re.match(r"^\d+:\d", l):
        entries[-1][2] += " " + l; continue
    entries.append([cur_t, len(entries), l])

con = sqlite3.connect(DB)
con.executescript("""
DROP TABLE IF EXISTS study_intros; DROP TABLE IF EXISTS study_notes; DROP TABLE IF EXISTS articles;
DROP TABLE IF EXISTS topics; DROP TABLE IF EXISTS topic_entries;
CREATE TABLE study_intros(book INTEGER NOT NULL, ord INTEGER NOT NULL, section TEXT NOT NULL, content TEXT NOT NULL);
CREATE TABLE study_notes(id INTEGER PRIMARY KEY, book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, chapter_end INTEGER NOT NULL, verse_end INTEGER NOT NULL, text TEXT NOT NULL);
CREATE TABLE articles(id INTEGER PRIMARY KEY, category TEXT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL);
CREATE TABLE topics(id INTEGER PRIMARY KEY, name TEXT NOT NULL);
CREATE TABLE topic_entries(topic INTEGER NOT NULL, ord INTEGER NOT NULL, text TEXT NOT NULL);
""")
con.executemany("INSERT INTO study_intros VALUES(?,?,?,?)", intros)
con.executemany("INSERT INTO study_notes(book,chapter,verse,chapter_end,verse_end,text) VALUES(?,?,?,?,?,?)", clean_notes)
con.executemany("INSERT INTO articles(id,category,title,content) VALUES(?,?,?,?)", [(o, c, t, x) for (c, o, t, x) in articles])
con.executemany("INSERT INTO topics VALUES(?,?)", list(enumerate(topics)))
con.executemany("INSERT INTO topic_entries VALUES(?,?,?)", [tuple(e) for e in entries])
con.execute("CREATE INDEX idx_sn ON study_notes(book,chapter,verse)")
con.commit()
from collections import Counter
cnt = Counter(n[0] for n in clean_notes)
print("intros", len(intros), "notes", len(clean_notes), "articles", len(articles), "topics", len(topics), "entries", len(entries))
print({BOOKS[b-1][0]: cnt[b] for b in range(1, 67)})
print("secciones:", Counter(x[2] for x in intros).most_common(40))
