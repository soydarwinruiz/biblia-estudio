# Códice vivo — Biblia de estudio (Android)

**Autor: Danruz**

Aplicación Android en Kotlin + Jetpack Compose. Todo el contenido (Biblias, notas de estudio, contenido diario)
viaja dentro del APK y tus datos personales se guardan solo en el teléfono. Internet se usa únicamente, si lo permites,
para comprobar una vez al día si hay una versión nueva (lee un archivo público; no envía datos).

## Contenido incluido
| Contenido | Cantidad | Fuente |
|---|---|---|
| Versiones de la Biblia | 12 (RVR1960, NBLA, LBLA, NVI, NVI España, NTV, RVA2015, RVC, DHH, DHH Estándar, TLA, TLAI) | `mrk214/bible-data-es-spa` |
| Notas versículo a versículo | ~17.000 | Biblia de Estudio MacArthur |
| Introducciones por libro | 66 libros: título, autor y fecha, contexto histórico, temas, retos de interpretación, bosquejo | MacArthur |
| Artículos | Cómo obtuvimos la Biblia, Cómo estudiar la Biblia, Pentateuco, Profetas, Período intertestamentario, Evangelios, Fe salvadora, Estudio panorámico de teología | MacArthur |
| Diccionario bíblico | 280 entradas: personas, lugares, términos, objetos, fiestas, grupos y nombres de Dios | redactado para la app |
| Índice temático | 613 temas, 23.000 entradas con referencias tocables | MacArthur |
| Referencias cruzadas | ~3.100 | RVR1960 |
| Guías de método | Inductivo, exégesis, hermenéutica, géneros literarios, contexto, homilética, devocional | propias |
| Versículos del día | 419 | selección propia |
| Pasajes del día | 224 | selección propia |
| Oraciones del día | 365 (una distinta cada día del año) | propias |
| Lecciones del día | 299 (panorama libro por libro + doctrina + método) | MacArthur + guías |
| Preguntas | 1.259 (179 escritas a mano + generadas: ¿dónde dice?, completa el versículo, autores, libros) | propias |
| Planes de lectura | 9 (Biblia en un año, AT+NT+Salmos, NT en 90 días, Evangelios, Salmos, Proverbios, Pablo, Pentateuco, Profetas) | generados |

> La Biblia de Estudio MacArthur y varias versiones bíblicas tienen derechos de autor. Este proyecto es **solo para uso personal**; no lo publiques ni lo distribuyas.

## Funciones
- **Hoy**: versículo, pasaje, lección, oración y preguntas del día (cambian cada día automáticamente), plan de lectura del día, racha, puntos y nivel. Notificación diaria configurable.
- **Biblia**: lector con 12 versiones, vista paralela, búsqueda de texto completo sin acentos, resaltado en 6 colores, notas personales, marcadores, temas, etiquetas, comparar un versículo en todas las versiones, copiar y compartir, marcar capítulos como leídos.
- **Estudio**: notas MacArthur por versículo y por capítulo con referencias tocables, introducciones a los libros, índice temático, artículos, lecciones y un **editor de estudio** por pasaje con pestañas de Contexto, Exégesis, Hermenéutica y Homilética (proposición, bosquejo, ilustraciones, aplicación y conclusión). Los estudios se pueden exportar como texto.
- **Mis temas**: temas creados por ti (con color y descripción) para agrupar versículos, etiquetas, y listas de resaltados, notas y marcadores.
- **Progreso**: porcentaje leído (Biblia, AT, NT y por libro), planes de lectura con calendario, 20 logros, niveles y rachas.
- **Preguntas**: 5 diarias (deterministas por fecha) y práctica libre por categoría.
- **Lectura en voz alta** del capítulo, el versículo del día, la oración y las lecciones (voz del teléfono, sin Internet).
- **Memorizar**: versículos con repaso espaciado (1, 2, 4, 7, 15, 30 y 60 días), pistas con la primera letra de cada palabra y puntos.
- **Palabras resaltadas**: marca palabras sueltas de un versículo en 6 colores y búscalas en el diccionario.
- **Lectura continua**: los capítulos se encadenan al desplazarse (menú de versión → Lectura continua).
- **Selector libro → capítulo → versículo** en el lector.
- **Widget** de pantalla de inicio con el versículo del día.
- **Continuar leyendo**, **días anteriores** del contenido diario, **deslizar** para cambiar de capítulo y **reflexiones guardadas** en las lecciones.
- **Ajustes**: tema claro, oscuro o sepia, colores del sistema (Material You), letra serif o sans, tamaño de letra, interlineado, versión predeterminada, recordatorio y **respaldo/restauración en JSON**.

### Novedades de la 1.9.2 (letra grande)
- Revisión de todas las pantallas con **pantalla estrecha y letra del sistema grande**: los textos de una línea se
  ajustan en vez de partirse (barra inferior, estadísticas de Hoy y Progreso, botones de Estudio, respuestas de
  Memorizar, cabecera de Preguntas, fecha y hora de la Agenda). La fecha de «Hoy» va bajo el saludo.
- **Lección**: «Completar la lección de hoy» está dentro de la tarjeta, bajo la reflexión; una vez hecha queda
  «✓ Lección de hoy completada». Abajo solo «‹ Anterior» y «Siguiente ›», del mismo ancho.
- Comparar ya no muestra «‹ v. 0»; el Diario no enseña un instante la lista de otra pestaña; plurales corregidos.
- Prueba nueva `LargeFontAuditTest` (360 dp, letra al 130 %) con capturas de 30 pantallas.

### Novedades de la 1.9.1 (correcciones)
- El **teclado** ya no tapa lo que escribes: el contenido se ajusta encima del teclado en toda la app y la barra
  inferior se oculta mientras escribes.
- **Lección del día**: la reflexión se guarda sola mientras escribes (y al salir); «Completar» la guarda, marca la
  lección con un aviso de puntos y queda como «Completada ✓» (antes no hacía nada visible si ya estaba marcada).

### Novedades de la 1.9
- **Recorrido de bienvenida**: antes de empezar, la app pide el nombre (opcional) y muestra en pocas pantallas
  cómo usar cada parte: Hoy, la Biblia, qué hacer al tocar un versículo, Aa y audio, Estudio, Diario, Temas y Progreso,
  y la privacidad de tus datos. Se puede saltar y repetir desde Ajustes › Ver el recorrido de bienvenida.
  Quien actualiza desde una versión anterior lo ve una vez.
- **Saludo personal** en «Hoy» y en el aviso diario: «Buenos días, Ana» (5–11 h), «Buenas tardes» (12–18 h),
  «Buenas noches» (19–4 h). El nombre se cambia en Ajustes › Tú.

### Novedades de la 1.8 (experiencia de uso)
- **Estudios**: se guardan solos mientras escribes y al salir con el gesto o el botón «atrás».
- **Deshacer**: al quitar un marcador, un versículo de un tema o de una etiqueta, una etiqueta completa, una nota,
  un resaltado o un versículo de Memorizar aparece «Deshacer» durante unos segundos.
- **Volver**: tras abrir una referencia desde el lector aparece «← Volver a Juan 3» (también vale el gesto atrás).
- **Lector**: botón **Aa** (tamaño, interlineado, letra, tema, pantalla siempre encendida, ocultar barras),
  **pellizcar** para agrandar el texto, barras que se ocultan al leer y vuelven al subir, barra de selección
  con las acciones principales y el resto en «Más»; menú ⋮ con «Escuchar capítulo», notas e introducción.
- **Resaltados** legibles en modo oscuro.
- La **notificación diaria** y el **widget** abren el versículo directamente.
- **Hoy**: las tarjetas completadas se pliegan a una línea; cualquier tarjeta se pliega tocando su título.
- **Búsqueda** mientras escribes.
- Accesibilidad: los botones de color tienen nombre para TalkBack.

### Novedades de la 1.7
- **Agenda**: actividades que se repiten (cada día, semana, 2 semanas, mes o «el primer lunes / último viernes»),
  con fecha de fin; cada repetición aparece en su día y tiene su aviso. Botón para **añadirla al calendario** del teléfono.
- **Lectura en voz alta con la pantalla apagada**, con controles en la notificación y en la pantalla de bloqueo
  (anterior, pausa/seguir donde quedó, siguiente, detener). Se pausa sola si entra una llamada.
- **Varias notas por versículo**.
- Los **puntos** ya no se repiten al desmarcar y volver a marcar un capítulo o un día de plan.
- Las **preguntas** no se repiten mientras queden otras sin contestar.
- **Temas**: la lista se desplaza sola al arrastrar un versículo cerca del borde.
- **Widgets** con colores de modo oscuro.
- Seguridad: **clave de firma nueva** con rotación (ver «Firma»). Requiere Android 9 o superior.

### Novedades de la 1.6
- **Diario** como sección propia en la barra inferior, con **Agenda** de la iglesia: cultos, ayunos, vigilias, devocionales,
  campañas, estudios, reuniones… con nombre, día, hora, lugar, notas y **aviso** (a la hora, 15 min, 1 h, 3 h o 1 día antes).
  Las próximas actividades aparecen también en «Hoy». Peticiones de oración y diario siguen en sus pestañas.
- **Temas**: los versículos quedan en el orden en que los agregas y se pueden **arrastrar** (asa ⠿) para reordenarlos,
  u ordenar por libro desde el menú.
- **Aviso de nuevas versiones** (Ajustes › Actualizaciones) y aviso de novedades al instalar una versión nueva.
- Correcciones: 1 y 2 Tesalonicenses se ven en el selector de libros; en el modo púlpito los controles ya no tapan el título.
- Mejoras: pestaña Pasaje del estudio más clara (capítulo completo / versículos, texto plegable, notas en tarjetas)
  y marca de nota MacArthur más discreta en el lector.

### Publicar una versión nueva (para que llegue el aviso)
1. Sube `versionCode` y `versionName` en `app/build.gradle.kts` y ejecuta `./gradlew assembleRelease`.
2. Crea una *Release* en GitHub con la etiqueta `vX.Y` y adjunta `codice-vivo-X.Y.apk`.
3. Actualiza `android-version.json` (en la raíz de este repositorio) con el nuevo `versionCode`, el nombre,
   el enlace de la Release y las notas, y súbelo a `main`. Las apps instaladas lo verán en las siguientes 24 horas.

**Sin computadora (GitHub Actions):** sube `versionCode` y `versionName` en `app/build.gradle.kts` (en `main`),
luego en GitHub ve a **Actions → Publicar versión → Run workflow** y escribe las notas. Compila y firma el APK,
crea la Release `vX.Y.Z` y actualiza `android-version.json` solo. Requiere, una sola vez, estos *secrets*
en **Settings → Secrets and variables → Actions**: `CODICE_KEYSTORE_BASE64` (la clave `.jks` en base64:
`base64 -w0 keystore/codice-vivo.jks`), `CODICE_STORE_PASSWORD`, `CODICE_KEY_PASSWORD` y `CODICE_KEY_ALIAS`.

### Novedades de la 1.5
- **Diario**: peticiones de oración con «respondida» (fecha y cómo respondió Dios) y entradas de diario; se puede orar desde un versículo (botón «Orar» del lector).
- **Respaldo automático** diario o semanal en una carpeta que elijas (se guardan las últimas 7 copias). La copia de Google del teléfono ahora sí incluye tus datos (antes la base de la Biblia, de ~110 MB, superaba el límite de 25 MB y no se respaldaba nada).
- **Recordatorio puntual**: usa alarmas del sistema (antes WorkManager podía retrasarlo horas), se reprograma tras reiniciar el teléfono y se pide el permiso de notificaciones en la bienvenida.
- **Pantalla de bienvenida** la primera vez: versión, tema y hora del recordatorio.
- **Memorizar con SM-2**: cuatro respuestas (otra vez, difícil, bien, fácil) con intervalos adaptativos y modo **escribir de memoria** que corrige palabra por palabra.
- **Búsqueda**: frase exacta, búsqueda en notas MacArthur y en tus notas, estudios y diario, historial de búsquedas. Además, la búsqueda por versión es mucho más rápida (de segundos a milisegundos).
- **Audio**: elegir voz (México, EE. UU., España…) y velocidad, temporizador para dormir y seguir con el siguiente capítulo.
- **Modo púlpito**: letra grande, pantalla siempre encendida y desplazamiento automático (desde el lector o un estudio).
- **Imagen del versículo** para compartir (4 estilos) y **estudios en PDF**.
- **Planes**: botón para reprogramar el plan si vas atrasado.
- **Widget «Mi día»** (racha, lectura del plan y versículos por repasar), **accesos directos** al mantener pulsado el ícono, y **«Compartir» / seleccionar texto** en cualquier app con una cita (p. ej. «Juan 3:16») para abrirla en Códice vivo.
- Lector más ligero: solo carga tus resaltados y notas de los capítulos visibles.

## Instalar el APK
Descarga el APK de la última versión en **[Releases](https://github.com/soydarwinruiz/biblia-estudio/releases/latest)**,
ábrelo en el teléfono y permite instalar apps de fuentes desconocidas. Requiere **Android 9 o superior**.
Se instala encima de la versión anterior sin perder datos. Los APK ya no se guardan en git (cada uno pesaba ~55 MB).

## Firma
**Desde la 1.9.4 la app se firma con otra clave nueva** (`keystore/codice-vivo.jks`, que **no** está en el repositorio).
La clave de la 1.7–1.9.2 se perdió, así que la 1.9.4 **no se instala encima** de esas versiones: hay que exportar el
respaldo (Ajustes → Respaldo de datos personales → Exportar), desinstalar, instalar la 1.9.4 e importar el respaldo.
A partir de la 1.9.4 las actualizaciones vuelven a instalarse encima sin perder datos. La clave y su contraseña están
en los *secrets* del repositorio (ver «Publicar una versión nueva»); guarda también una copia fuera de GitHub.

(Historia: en la 1.7 se aplicó la rotación de clave de Android con `keystore/rotacion.lineage` porque la clave original
quedó publicada; ese archivo se retiró en la 1.9.4 al cambiar de clave.)

Para compilar una versión firmada: copia `keystore.properties.example` como `keystore.properties` con la contraseña,
pon la clave en `keystore/` y ejecuta `./gradlew assembleRelease`. El APK listo para publicar sale en
`app/build/outputs/apk/release/codice-vivo-<versión>.apk` (firmado con la clave).

## Compilar
Requisitos: JDK 17+ y Android SDK (plataforma 35). El script `./gradlew` descarga la versión correcta de Gradle.
```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/codice-vivo-1.9.4.apk
./gradlew testDebugUnitTest    # 37 pruebas: contenido, búsqueda, respaldo, migraciones, temas, agenda y repeticiones, audio, puntos, SM-2 y pantallas
```

## Regenerar la base de contenido
```bash
git clone https://github.com/mrk214/bible-data-es-spa
pdftotext Biblia_de_estudio_MacArthur.pdf mac.txt
tools/build_all.sh bible-data-es-spa mac.txt bible.db
gzip -c bible.db > app/src/main/assets/bible_content.bin
```
Después incrementa `CONTENT_VERSION` en `ContentRepository.kt` para que la app copie la nueva base.
El contenido diario editable está en `tools/content/` (versículos, pasajes, oraciones, preguntas y guías).

## Estructura
```
tools/                       Scripts de Python que generan bible.db
app/src/main/java/.../data   content/ (SQLite de solo lectura), user/ (Room: datos personales), prefs/ (DataStore)
app/src/main/java/.../domain Analizador de referencias, gamificación y logros
app/src/main/java/.../ui     Pantallas en Compose
app/src/main/java/.../work   Recordatorio (AlarmManager), widgets y respaldo automático (WorkManager)
```

## Versión para iPhone
La app web (PWA) está en el repositorio público `codice-vivo-web` (solo código).
Su biblioteca es `biblioteca/codice-vivo-biblioteca.db.gz` (generada con `tools/build_web_library.py`, índice FTS5).
Descárgala en el iPhone y elígela en la app la primera vez.
