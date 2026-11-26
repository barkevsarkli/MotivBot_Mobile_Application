package com.motivbot.app.ui.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.motivbot.app.R
import com.motivbot.app.domain.ResultSummary
import com.motivbot.app.ui.components.DSTertiaryButton
import com.motivbot.app.ui.components.pressable
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSFont
import com.motivbot.app.ui.theme.DSRadius
import com.motivbot.app.ui.theme.DSSize
import com.motivbot.app.ui.theme.DSSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Port of ResultExportPanel.swift.

/** Opens the share sheet for [file] through the FileProvider. */
fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser_title)))
}

/**
 * The sheet behind the download button on the results screen: a preview of the summary page and a choice
 * between PDF and PNG (and the CSV, when there is one). It renders the chosen file, closes, then opens
 * the share sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportOptionsSheet(summary: ResultSummary, canShareCsv: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = DS.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // The format being rendered right now; its row shows a spinner and both rows are disabled.
    var working by remember { mutableStateOf<ResultExportFormat?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun closeThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            onDismiss()
            action()
        }
    }

    fun export(format: ResultExportFormat) {
        if (working != null) return
        working = format
        scope.launch {
            // A short pause lets the row show its spinner first.
            delay(60)
            val file = withContext(Dispatchers.Default) {
                runCatching { ResultExporter.makeFile(context, format, summary) }.getOrNull()
            }
            working = null
            if (file == null) {
                failed = true
            } else {
                closeThen { shareFile(context, file, format.mimeType) }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.stroke) },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DSSpacing.screen)
                .padding(top = DSSpacing.s, bottom = DSSpacing.m)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.m),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xxs)) {
                Text(
                    stringResource(R.string.export_title),
                    style = DSFont.title,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.export_body), style = DSFont.callout, color = colors.textSecondary)
            }

            ExportHero(summary)

            Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                ExportOptionRow(
                    title = stringResource(R.string.export_pdf_title),
                    detail = stringResource(R.string.export_pdf_detail),
                    icon = Icons.Outlined.Description,
                    tint = colors.brand,
                    softTint = colors.brandSoft,
                    isWorking = working == ResultExportFormat.Pdf,
                    enabled = working == null,
                ) { export(ResultExportFormat.Pdf) }
                ExportOptionRow(
                    title = stringResource(R.string.export_image_title),
                    detail = stringResource(R.string.export_image_detail),
                    icon = Icons.Outlined.Image,
                    tint = colors.sky,
                    softTint = colors.skySoft,
                    isWorking = working == ResultExportFormat.Image,
                    enabled = working == null,
                ) { export(ResultExportFormat.Image) }
            }

            if (canShareCsv) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DSTertiaryButton(stringResource(R.string.share_results_button), icon = Icons.Outlined.Share) {
                        val csv = ExportFiles.csvFile(context)
                        if (csv == null) failed = true else closeThen { shareFile(context, csv, "text/csv") }
                    }
                }
            }
        }
    }

    if (failed) {
        AlertDialog(
            onDismissRequest = { failed = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.export_failed), style = DSFont.heading, color = colors.textPrimary) },
            confirmButton = {
                TextButton(onClick = { failed = false }) {
                    Text(stringResource(R.string.ok_button), style = DSFont.label, color = colors.brand)
                }
            },
        )
    }
}

/** The page preview on a soft brand panel with a second sheet tilted behind it. */
@Composable
private fun ExportHero(summary: ResultSummary) {
    val colors = DS.colors
    val reduceMotion = DS.reduceMotion
    val width = 112.dp
    val height = width * (SummaryPagePainter.PAGE_HEIGHT / SummaryPagePainter.PAGE_WIDTH)
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.brandSoft, RoundedCornerShape(DSRadius.medium))
            .padding(vertical = DSSpacing.m)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // A second sheet behind the page hints that it can be saved in two formats.
        Box(
            Modifier
                .offset(x = 14.dp, y = 4.dp)
                .rotate(if (reduceMotion) 0f else 7f)
                .size(width, height)
                .background(colors.surface.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
        )
        SummaryPagePreview(summary, width, Modifier.rotate(if (reduceMotion) 0f else -4f))
    }
}

/** A small, live picture of the summary page, so people see what they are about to save. */
@Composable
fun SummaryPagePreview(summary: ResultSummary, width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val height = width * (SummaryPagePainter.PAGE_HEIGHT / SummaryPagePainter.PAGE_WIDTH)
    val pixelsPerPoint = with(density) { width.toPx() } / SummaryPagePainter.PAGE_WIDTH
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(summary, pixelsPerPoint) {
        bitmap = withContext(Dispatchers.Default) {
            runCatching { ResultExporter.render(SummaryPagePainter(context, summary), pixelsPerPoint).asImageBitmap() }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(width, height)
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.2f))
            .clip(shape)
            .background(Color.White)
            .border(0.5.dp, Color.Black.copy(alpha = 0.08f), shape),
    ) {
        bitmap?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.FillWidth, alignment = Alignment.TopCenter, modifier = Modifier.size(width, height))
        }
    }
}

/** One way to save the summary: a tinted format tile, a title, a short detail and a save symbol. */
@Composable
private fun ExportOptionRow(
    title: String,
    detail: String,
    icon: ImageVector,
    tint: Color,
    softTint: Color,
    isWorking: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = DS.colors
    val shape = RoundedCornerShape(DSRadius.medium)
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DSSize.answerOptionMinHeight)
            .pressable(enabled = enabled && !isWorking, onClick = onClick)
            .background(colors.surfaceMuted, shape)
            .border(1.dp, colors.stroke, shape)
            .padding(DSSpacing.s)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(softTint, RoundedCornerShape(DSRadius.small)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = DSFont.bodyEmphasis, color = colors.textPrimary)
            Text(detail, style = DSFont.footnote, color = colors.textSecondary)
        }
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (isWorking) {
                CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Outlined.Download, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
        }
    }
}
