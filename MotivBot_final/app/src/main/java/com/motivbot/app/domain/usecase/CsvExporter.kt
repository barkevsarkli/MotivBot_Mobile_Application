package com.motivbot.app.domain.usecase

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of `WellBeingViewModel.generateCSV()` plus the temp-file write that followed it.
 *
 * Two fixes over the Swift original, both about correctness rather than style:
 *  - it wrote `try? csv.write(...)` — a silent failure that left `csvFileURL` pointing at a file
 *    that may not exist. Here the failure is returned to the caller;
 *  - it "escaped" commas by replacing them with semicolons, which corrupts the user's free-text
 *    answers. [csvField] does real RFC-4180 quoting instead, so the text round-trips intact.
 */
@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /** Swift: `"Section,Question,Answer,Score\n"` header, same column order. */
    fun buildCsv(rows: List<AnswerRow>): String = buildString {
        append("Section,Question,Answer,Score\r\n")
        rows.forEach { row ->
            append(csvField(row.sectionTitle)).append(',')
            append(csvField(row.questionText)).append(',')
            append(csvField(row.answerText)).append(',')
            append(csvField(row.score?.toString() ?: "N/A")).append("\r\n")
        }
    }

    /**
     * Writes the CSV into the FileProvider-visible cache dir and returns a shareable `content://`
     * URI — the Android counterpart of handing a temp-file `URL` to `UIActivityViewController`.
     */
    suspend fun writeCsv(rows: List<AnswerRow>, fileName: String = DEFAULT_FILE_NAME): Result<android.net.Uri> =
        withContext(ioDispatcher) {
            runCatching {
                val exportsDir = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }
                val file = File(exportsDir, fileName)
                file.writeText(buildCsv(rows), Charsets.UTF_8)

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }
        }

    /** Quotes a field only when it needs it, doubling any embedded quote (RFC 4180). */
    private fun csvField(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"${value.replace("\"", "\"\"")}\"" else value
    }

    private companion object {
        const val EXPORTS_DIR = "exports"
        const val DEFAULT_FILE_NAME = "SurveyAnswers.csv"
    }
}
