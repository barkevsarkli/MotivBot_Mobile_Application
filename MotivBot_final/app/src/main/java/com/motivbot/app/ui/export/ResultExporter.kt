package com.motivbot.app.ui.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.createBitmap
import com.motivbot.app.domain.ResultSummary
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The formats the results summary can be exported in. */
enum class ResultExportFormat(val fileExtension: String, val mimeType: String) {
    Pdf("pdf", "application/pdf"),
    Image("png", "image/png"),
}

/** Where exported files live: the app's private cache, shared through the FileProvider ("exports/"). */
object ExportFiles {
    private const val CSV_NAME = "SurveyAnswers.csv"

    fun directory(context: Context): File = File(context.cacheDir, "exports")

    /** Writes the CSV shared with the results; returns the file, or null when it could not be written. */
    fun writeCsv(context: Context, csv: String): File? = runCatching {
        val dir = directory(context).apply { mkdirs() }
        val file = File(dir, CSV_NAME)
        val tmp = File(dir, "$CSV_NAME.tmp")
        tmp.writeText(csv, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file))
        }
        file
    }.getOrNull()

    fun csvFile(context: Context): File? = File(directory(context), CSV_NAME).takeIf { it.exists() }

    fun deleteCsv(context: Context) {
        File(directory(context), CSV_NAME).delete()
    }

    /** Deletes every exported PDF and image (not the CSV, which lives as long as the result). */
    fun removeExports(context: Context) {
        directory(context).listFiles()?.forEach { file ->
            if (file.name.startsWith("MotivBot-")) file.delete()
        }
    }
}

/**
 * Port of `ResultExporter.swift`: renders the summary page to a one-page A4 PDF or a PNG.
 * Files go to the app's private cache and are removed before the next export, on restart and at launch.
 */
object ResultExporter {

    fun makeFile(context: Context, format: ResultExportFormat, summary: ResultSummary): File {
        ExportFiles.removeExports(context)
        val dir = ExportFiles.directory(context).apply { mkdirs() }
        val day = DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(summary.dateMillis).atZone(ZoneId.systemDefault()))
        val file = File(dir, "MotivBot-$day.${format.fileExtension}")
        val painter = SummaryPagePainter(context, summary)
        FileOutputStream(file).use { out ->
            when (format) {
                ResultExportFormat.Pdf -> writePdf(painter, out)
                ResultExportFormat.Image -> writePng(painter, out)
            }
        }
        return file
    }

    private fun writePdf(painter: SummaryPagePainter, out: FileOutputStream) {
        val width = SummaryPagePainter.PAGE_WIDTH
        val height = SummaryPagePainter.PAGE_HEIGHT
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(width.toInt(), height.toInt(), 1).create())
            val canvas = page.canvas
            // Keep it on one page: shrink long content to fit, centre it and pin it to the top.
            val contentHeight = painter.contentHeight
            val scale = minOf(1f, height / maxOf(contentHeight, 1f))
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.translate((width - width * scale) / 2, 0f)
            canvas.scale(scale, scale)
            painter.draw(canvas)
            document.finishPage(page)
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun writePng(painter: SummaryPagePainter, out: FileOutputStream) {
        val bitmap = render(painter, scale = 2f)
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        } finally {
            bitmap.recycle()
        }
    }

    /** The full page at [scale] pixels per point, on white. */
    fun render(painter: SummaryPagePainter, scale: Float): Bitmap {
        val width = (SummaryPagePainter.PAGE_WIDTH * scale).toInt()
        val height = (painter.contentHeight * scale).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(scale, scale)
        painter.draw(canvas)
        return bitmap
    }
}
