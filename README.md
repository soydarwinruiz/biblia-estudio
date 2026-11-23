# Códice vivo — Biblia de estudio (Android, 100% local)

Aplicación Android en Kotlin + Jetpack Compose. **No solicita permiso de Internet**: todo el contenido
(Biblias, notas de estudio, contenido diario) viaja dentro del APK y tus datos personales se guardan solo en el teléfono.

## Contenido incluido
| Contenido | Cantidad | Fuente |
|---|---|---|
| Versiones de la Biblia | 12 (RVR1960, NBLA, LBLA, NVI, NVI España, NTV, RVA2015, RVC, DHH, DHH Estándar, TLA, TLAI) | `mrk214/bible-data-es-spa` |
| Notas versículo a versículo | ~17.000 | Biblia de Estudio MacArthur |
| Introducciones por libro | 66 libros: título, autor y fecha, contexto histórico, temas, retos de interpretación, bosquejo | MacArthur |
| Artículos | Cómo obtuvimos la Biblia, Cómo estudiar la Biblia, Pentateuco, Profetas, Período intertestamentario, Evangelios, Fe salvadora, Estudio panorámico de teología | MacArthur |
| Índice temático | 613 temas, 23.000 entradas con referencias tocables | MacArthur |
| Referencias cruzadas | ~3.100 | RVR1960 |
| Guías de método | Inductivo, exégesis, hermenéutica, géneros literarios, contexto, homilética, devocional | propias |
| Versículos del día | 419 | selección propia |
| Pasajes del día | 224 | selección propia |
| Oraciones del día | 62 | propias |
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
- **Widget** de pantalla de inicio con el versículo del día.
- **Continuar leyendo**, **días anteriores** del contenido diario, **deslizar** para cambiar de capítulo y **reflexiones guardadas** en las lecciones.
- **Ajustes**: tema claro, oscuro o sepia, tamaño de letra, interlineado, versión predeterminada, recordatorio y **respaldo/restauración en JSON**.

## Instalar el APK
Descarga [`apk/codice-vivo-1.3.apk`](apk/codice-vivo-1.3.apk) en el teléfono (botón *Download raw file*), ábrelo y permite instalar apps de fuentes desconocidas.

## Firma
El APK se firma con `keystore/biblia-release.jks` (contraseñas en `app/build.gradle.kts`). Usa siempre esta misma clave
para que las actualizaciones se instalen sobre la versión anterior sin perder datos. Mantén el repositorio privado.

## Compilar
Requisitos: JDK 17+, Android SDK (plataforma 35) y Gradle 8.9 o superior.
```bash
./gradlew assembleRelease      # o: gradle assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
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
app/src/main/java/.../work   Notificación diaria (WorkManager)
```
