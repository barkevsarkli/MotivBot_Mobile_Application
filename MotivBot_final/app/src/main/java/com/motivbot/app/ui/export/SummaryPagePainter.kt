package com.motivbot.app.ui.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import com.motivbot.app.R
import com.motivbot.app.core.localized
import com.motivbot.app.domain.MessageCategoryStyle
import com.motivbot.app.domain.ResultSummary
import com.motivbot.app.ui.components.color
import com.motivbot.app.ui.components.icon
import com.motivbot.app.ui.longDate
import com.motivbot.app.ui.theme.DSColors

/**
 * Port of `ResultSummaryPage.swift`: the one-page results summary for the PDF and image export.
 *
 * It shows the date, the five dimensions (levels only), the messages and the disclaimer; never the free
 * text or individual answers. It is always light and keeps its meaning in black and white: levels are
 * filled versus outlined steps, and every colour is backed by text. Sizes are in points (1/72 inch);
 * the page is A4, 595 × 842.
 */
class SummaryPagePainter(context: Context, private val summary: ResultSummary) {

    private val resources = context.localized(summary.language).resources
    private val rtl = summary.language.isRtl
    private val colors = DSColors.Light
    private val contentWidth = PAGE_WIDTH - MARGIN * 2

    private fun string(id: Int) = resources.getString(id)

    /** The height of the whole page content, margins included. At least the A4 height is not enforced. */
    val contentHeight: Float by lazy { layout(null) }

    /** Draws the page at the canvas origin, [PAGE_WIDTH] wide and [contentHeight] tall, on white. */
    fun draw(canvas: Canvas) {
        canvas.drawColor(android.graphics.Color.WHITE)
        layout(canvas)
    }

    // MARK: - Layout

    /** Lays the page out top to bottom; draws when [canvas] is set. Returns the total height. */
    private fun layout(canvas: Canvas?): Float {
        var y = MARGIN
        y += header(canvas, y) + BLOCK_SPACING
        if (canvas != null) fillRect(canvas, 0f, y, contentWidth, 1f, colors.stroke.toArgb())
        y += 1f + BLOCK_SPACING
        if (summary.elevatedRisk) y += support(canvas, y) + BLOCK_SPACING
        y += dimensions(canvas, y) + BLOCK_SPACING
        if (summary.messages.isNotEmpty()) y += messages(canvas, y) + BLOCK_SPACING
        y += disclaimer(canvas, y) + BLOCK_SPACING
        y += text(canvas, string(R.string.export_footer), 0f, y, contentWidth, paint(8f, colors.textSecondary.toArgb()), center = true)
        return y + MARGIN
    }

    private fun header(canvas: Canvas?, top: Float): Float {
        val datePaint = paint(11f, colors.textSecondary.toArgb(), Weight.Medium)
        val date = summary.language.longDate(summary.dateMillis)
        val dateWidth = datePaint.measureText(date)
        val iconSize = 18f
        val textStart = iconSize + 10f
        val textWidth = (contentWidth - textStart - dateWidth - 12f).coerceAtLeast(80f)
        val titlePaint = paint(18f, colors.textPrimary.toArgb(), Weight.Bold)
        val subtitlePaint = paint(11f, colors.textSecondary.toArgb())
        val title = staticLayout(string(R.string.app_title), titlePaint, textWidth)
        val subtitle = staticLayout(string(R.string.export_page_title), subtitlePaint, textWidth)
        val stackHeight = title.height + 2f + subtitle.height
        val dateLayout = staticLayout(date, datePaint, dateWidth + 1f)
        val height = maxOf(stackHeight, iconSize, dateLayout.height.toFloat())
        if (canvas != null) {
            icon(canvas, Icons.Filled.Eco, 0f, top + (height - iconSize) / 2, iconSize, colors.brand.toArgb())
            val stackTop = top + (height - stackHeight) / 2
            drawLayout(canvas, title, textStart, stackTop, textWidth)
            drawLayout(canvas, subtitle, textStart, stackTop + title.height + 2f, textWidth)
            drawLayout(canvas, dateLayout, contentWidth - dateWidth - 1f, top + (height - dateLayout.height) / 2, dateWidth + 1f)
        }
        return height
    }

    private fun support(canvas: Canvas?, top: Float): Float {
        val padding = 12f
        val innerWidth = contentWidth - padding * 2
        val title = staticLayout(string(R.string.support_title), paint(12f, colors.textPrimary.toArgb(), Weight.Semibold), innerWidth)
        val body = staticLayout(string(R.string.support_body), paint(10f, colors.textPrimary.toArgb()), innerWidth)
        val height = padding * 2 + title.height + 4f + body.height
        if (canvas != null) {
            strokeRoundRect(canvas, 0f, top, contentWidth, height, 8f, colors.care.toArgb(), 1f)
            drawLayout(canvas, title, padding, top + padding, innerWidth)
            drawLayout(canvas, body, padding, top + padding + title.height + 4f, innerWidth)
        }
        return height
    }

    private fun dimensions(canvas: Canvas?, top: Float): Float {
        var y = top
        y += sectionTitle(canvas, string(R.string.export_dimensions_title), y)
        val iconFrame = 18f
        val textStart = iconFrame + 12f
        val textWidth = contentWidth - textStart
        val indicatorWidth = 18f * 3 + 4f * 2
        summary.dimensions.forEach { row ->
            y += 12f
            val tone = row.tone.color(colors).toArgb()
            val title = staticLayout(
                string(row.dimension.titleRes),
                paint(11f, colors.textPrimary.toArgb(), Weight.Semibold),
                textWidth - indicatorWidth - 8f,
            )
            val message = staticLayout(
                string(row.dimension.messageRes(row.level)),
                paint(10f, colors.textSecondary.toArgb()),
                textWidth,
                lineSpacing = if (rtl) 3f else 1f,
            )
            val titleRowHeight = maxOf(title.height.toFloat(), 8f)
            if (canvas != null) {
                icon(canvas, row.dimension.icon, (iconFrame - 12f) / 2, y + 1f, 12f, tone)
                drawLayout(canvas, title, textStart, y, textWidth - indicatorWidth - 8f)
                levelIndicator(canvas, contentWidth - indicatorWidth, y + (titleRowHeight - 8f) / 2, row.level.filledSteps, tone)
                drawLayout(canvas, message, textStart, y + titleRowHeight + 3f, textWidth)
            }
            y += titleRowHeight + 3f + message.height
        }
        return y - top
    }

    private fun messages(canvas: Canvas?, top: Float): Float {
        var y = top
        y += sectionTitle(canvas, string(R.string.messages_title), y)
        val textStart = 2f + 10f
        val textWidth = contentWidth - textStart
        MessageCategoryStyle.ordered(summary.messages).forEach { message ->
            y += 10f
            val style = MessageCategoryStyle.of(message.category)
            val tint = style.color(colors).toArgb()
            val category = staticLayout(string(style.titleRes), paint(9f, tint, Weight.Semibold), textWidth)
            val body = staticLayout(message.text, paint(10f, colors.textPrimary.toArgb()), textWidth, lineSpacing = if (rtl) 3f else 1f)
            val author = message.author?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { staticLayout(it, paint(9f, colors.textSecondary.toArgb()), textWidth) }
            val height = category.height + 3f + body.height + (author?.let { 3f + it.height } ?: 0f)
            if (canvas != null) {
                fillRect(canvas, 0f, y, 2f, height, tint)
                drawLayout(canvas, category, textStart, y, textWidth)
                drawLayout(canvas, body, textStart, y + category.height + 3f, textWidth)
                author?.let { drawLayout(canvas, it, textStart, y + category.height + 3f + body.height + 3f, textWidth) }
            }
            y += height
        }
        return y - top
    }

    private fun disclaimer(canvas: Canvas?, top: Float): Float {
        val padding = 10f
        val iconSize = 10f
        val textStart = padding + iconSize + 8f
        val textWidth = contentWidth - textStart - padding
        val body = staticLayout(string(R.string.result_disclaimer), paint(10f, colors.textPrimary.toArgb()), textWidth)
        val height = padding * 2 + body.height
        if (canvas != null) {
            strokeRoundRect(canvas, 0f, top, contentWidth, height, 8f, colors.textSecondary.toArgb(), 0.75f)
            icon(canvas, Icons.Outlined.Info, padding, top + padding + 1.5f, iconSize, colors.textPrimary.toArgb())
            drawLayout(canvas, body, textStart, top + padding, textWidth)
        }
        return height
    }

    private fun sectionTitle(canvas: Canvas?, value: String, top: Float): Float =
        text(canvas, value, 0f, top, contentWidth, paint(13f, colors.textPrimary.toArgb(), Weight.Semibold))

    private fun levelIndicator(canvas: Canvas, start: Float, top: Float, filledSteps: Int, color: Int) {
        repeat(3) { step ->
            val x = start + step * (18f + 4f)
            if (step < filledSteps) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                canvas.drawRoundRect(mirrored(x, top, 18f, 8f), 3f, 3f, paint)
            } else {
                strokeRoundRect(canvas, x, top, 18f, 8f, 3f, androidx.core.graphics.ColorUtils.setAlphaComponent(color, 153), 1.25f)
            }
        }
    }

    // MARK: - Drawing primitives (start-based x, mirrored for right-to-left pages)

    private enum class Weight { Regular, Medium, Semibold, Bold }

    private fun paint(size: Float, color: Int, weight: Weight = Weight.Regular) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = when (weight) {
            Weight.Regular -> Typeface.DEFAULT
            Weight.Medium -> weighted(500)
            Weight.Semibold -> weighted(600)
            Weight.Bold -> Typeface.DEFAULT_BOLD
        }
    }

    private fun weighted(weight: Int): Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, weight, false)
        } else if (weight >= 600) {
            Typeface.DEFAULT_BOLD
        } else {
            Typeface.DEFAULT
        }

    private fun staticLayout(value: String, paint: TextPaint, width: Float, lineSpacing: Float = 0f, center: Boolean = false): StaticLayout =
        StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setLineSpacing(lineSpacing, 1f)
            .setIncludePad(false)
            .build()

    private fun text(canvas: Canvas?, value: String, start: Float, top: Float, width: Float, paint: TextPaint, center: Boolean = false): Float {
        val layout = staticLayout(value, paint, width, center = center)
        if (canvas != null) drawLayout(canvas, layout, start, top, width)
        return layout.height.toFloat()
    }

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, start: Float, top: Float, width: Float) {
        canvas.save()
        canvas.translate(MARGIN + mirroredX(start, width), top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun mirroredX(start: Float, width: Float): Float = if (rtl) contentWidth - start - width else start

    private fun mirrored(start: Float, top: Float, width: Float, height: Float): RectF {
        val left = MARGIN + mirroredX(start, width)
        return RectF(left, top, left + width, top + height)
    }

    private fun fillRect(canvas: Canvas, start: Float, top: Float, width: Float, height: Float, color: Int) {
        canvas.drawRect(mirrored(start, top, width, height), Paint().apply { this.color = color })
    }

    private fun strokeRoundRect(canvas: Canvas, start: Float, top: Float, width: Float, height: Float, radius: Float, color: Int, lineWidth: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = lineWidth
            this.color = color
        }
        val rect = mirrored(start, top, width, height).apply { inset(lineWidth / 2, lineWidth / 2) }
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    /** Draws a Material icon (24 × 24 viewport) [size] points wide. */
    private fun icon(canvas: Canvas, vector: ImageVector, start: Float, top: Float, size: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val rect = mirrored(start, top, size, size)
        canvas.save()
        canvas.translate(rect.left, rect.top)
        canvas.scale(size / vector.viewportWidth, size / vector.viewportHeight)
        drawNode(canvas, vector.root, paint)
        canvas.restore()
    }

    private fun drawNode(canvas: Canvas, node: VectorNode, paint: Paint) {
        when (node) {
            is VectorGroup -> node.forEach { drawNode(canvas, it, paint) }
            is VectorPath -> {
                val path = node.pathData.toPath().asAndroidPath()
                if (node.pathFillType == PathFillType.EvenOdd) path.fillType = android.graphics.Path.FillType.EVEN_ODD
                canvas.drawPath(path, paint)
            }
        }
    }

    companion object {
        const val PAGE_WIDTH = 595f
        const val PAGE_HEIGHT = 842f
        const val MARGIN = 40f
        private const val BLOCK_SPACING = 20f
    }
}
