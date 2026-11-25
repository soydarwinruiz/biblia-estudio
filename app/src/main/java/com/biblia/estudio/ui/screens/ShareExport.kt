package com.biblia.estudio.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/** Estilos de las imágenes para compartir: degradado (arriba, abajo), color del texto y de la referencia. */
private data class CardStyle(val name: String, val top: Int, val bottom: Int, val text: Int, val accent: Int)

private val styles = listOf(
    CardStyle("Pergamino", 0xFFFFF8EE.toInt(), 0xFFEBD3B0.toInt(), 0xFF2E1A0C.toInt(), 0xFF7A4A1E.toInt()),
    CardStyle("Noche", 0xFF1A1411.toInt(), 0xFF3E2A1C.toInt(), 0xFFF5EBDD.toInt(), 0xFFFFB77A.toInt()),
    CardStyle("Olivo", 0xFF4E5A2E.toInt(), 0xFF27301A.toInt(), 0xFFF4F1E6.toInt(), 0xFFE0E7B1.toInt()),
    CardStyle("Cielo", 0xFF1E3A5F.toInt(), 0xFF6B8CAE.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFE08A.toInt()),
)

/** Dibuja el versículo en una imagen cuadrada de 1080 px, ajustando el tamaño de letra para que quepa. */
private fun renderVerseCard(text: String, ref: String, st: CardStyle, size: Int = 1080): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, size.toFloat(), st.top, st.bottom, Shader.TileMode.CLAMP) })
    val margin = size * 0.1f
    val width = (size - 2 * margin).toInt()
    val quote = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = st.text; typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) }
    var fs = size * 0.075f
    lateinit var layout: StaticLayout
    while (true) {
        quote.textSize = fs
        layout = StaticLayout.Builder.obtain("«$text»", 0, text.length + 2, quote, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.25f).build()
        if (layout.height <= size * 0.62f || fs < size * 0.025f) break
        fs *= 0.92f
    }
    val refPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = st.accent; textSize = size * 0.04f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    val top = (size - layout.height - size * 0.1f) / 2
    cv.save(); cv.translate(margin, top); layout.draw(cv); cv.restore()
    cv.drawText(ref, size / 2f, top + layout.height + size * 0.09f, refPaint)
    val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = st.accent; alpha = 150; textSize = size * 0.026f; textAlign = Paint.Align.CENTER; letterSpacing = 0.15f }
    cv.drawText("CÓDICE VIVO", size / 2f, size - size * 0.05f, brand)
    return bmp
}

/** Guarda un archivo en la caché compartible y abre el menú «Compartir». */
fun shareFile(context: Context, name: String, mime: String, write: (File) -> Unit) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val f = File(dir, name); write(f)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun VerseImageDialog(text: String, ref: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var styleIdx by remember { mutableIntStateOf(0) }
    val bmp = remember(styleIdx, text, ref) { renderVerseCard(text, ref, styles[styleIdx], 720) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Imagen para compartir") },
        text = {
            Column {
                Image(bmp.asImageBitmap(), "Vista previa", Modifier.fillMaxWidth().aspectRatio(1f))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    styles.forEachIndexed { i, st ->
                        Box(Modifier.size(36.dp).clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(st.top), Color(st.bottom))))
                            .then(if (i == styleIdx) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                            .clickable { styleIdx = i })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val full = renderVerseCard(text, ref, styles[styleIdx])
                shareFile(ctx, "versiculo.png", "image/png") { f -> f.outputStream().use { full.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                onDismiss()
            }) { Text("Compartir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/**
 * Genera un PDF A4 a partir de secciones (título, cuerpo). Las líneas que empiezan por «# » se
 * tratan como encabezados. Pagina automáticamente.
 */
fun writePdf(file: File, title: String, subtitle: String, sections: List<Pair<String, String>>) {
    val pageW = 595; val pageH = 842; val margin = 56f; val width = (pageW - 2 * margin).toInt()
    val doc = PdfDocument()
    val titleP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); color = 0xFF2E1A0C.toInt() }
    val subP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xFF7A4A1E.toInt() }
    val headP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = 0xFF7A4A1E.toInt() }
    val bodyP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.SERIF; color = 0xFF222222.toInt() }
    var pageNo = 0
    var page: PdfDocument.Page? = null
    var y = 0f
    fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        y = margin
        page!!.canvas.drawText("$pageNo", pageW / 2f, pageH - 28f, subP.apply { textAlign = Paint.Align.CENTER })
        subP.textAlign = Paint.Align.LEFT
    }
    fun layout(text: String, p: TextPaint) = StaticLayout.Builder.obtain(text, 0, text.length, p, width).setLineSpacing(0f, 1.3f).build()
    /** Dibuja un bloque; si no cabe, lo parte línea a línea entre páginas. */
    fun draw(text: String, p: TextPaint, after: Float) {
        val l = layout(text, p)
        var line = 0
        while (line < l.lineCount) {
            if (page == null || y + (l.getLineBottom(line) - l.getLineTop(line)) > pageH - margin) newPage()
            val start = line
            var h = 0f
            while (line < l.lineCount && y + h + (l.getLineBottom(line) - l.getLineTop(line)) <= pageH - margin) { h += l.getLineBottom(line) - l.getLineTop(line); line++ }
            val cv = page!!.canvas
            cv.save(); cv.translate(margin, y - l.getLineTop(start)); cv.clipRect(0f, l.getLineTop(start).toFloat(), width.toFloat(), l.getLineTop(start) + h); l.draw(cv); cv.restore()
            y += h
        }
        y += after
    }
    newPage()
    draw(title, titleP, 4f)
    draw(subtitle, subP, 18f)
    sections.filter { it.second.isNotBlank() }.forEach { (h, body) ->
        if (y > pageH - margin - 60) newPage()
        if (h.isNotBlank()) draw(h, headP, 6f)
        body.split("\n").forEach { para -> if (para.startsWith("# ")) draw(para.removePrefix("# "), headP, 6f) else draw(para, bodyP, 6f) }
        y += 8f
    }
    page?.let { doc.finishPage(it) }
    file.outputStream().use { doc.writeTo(it) }
    doc.close()
}
