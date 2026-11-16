package com.pennywiseai.tracker.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.core.content.FileProvider
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.review.CurrencyReview
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.inAppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A piece of the report that is never split across pages. */
internal class ReportBlock(
    val height: Float,
    /** A heading: it moves to the next page with the block after it rather than end a page alone. */
    val keepWithNext: Boolean = false,
    val draw: Canvas.(top: Float) -> Unit = {},
)

/**
 * The index of the first block on each page, filling [pageHeight] in order. A block
 * taller than a page still gets a page to itself; one never starts a page empty.
 */
internal fun pageStarts(blocks: List<ReportBlock>, pageHeight: Float): List<Int> {
    if (blocks.isEmpty()) return listOf(0)
    val starts = mutableListOf(0)
    var used = 0f
    blocks.forEachIndexed { index, block ->
        // A run of headings (a title, then a table's column names) travels with the first row after it.
        var needed = block.height
        var next = index
        while (blocks[next].keepWithNext && next + 1 < blocks.size) needed += blocks[++next].height
        if (used > 0f && used + needed > pageHeight) {
            starts += index
            used = 0f
        }
        used += block.height
    }
    return starts
}

/** The share sheet for a report [PdfReportExporter.export] wrote. */
fun pdfShareIntent(uri: Uri): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, null)
}

/**
 * A month's or year's review as a PDF, drawn on-device with [PdfDocument]: the same
 * [PeriodReview] the Review screen shows, so the totals match it. Amounts are always
 * printed (the file leaves the screen), and nothing carries an SMS body, an account
 * number or a receipt.
 *
 * Text is laid out with [StaticLayout] in the app's language and direction, so Arabic
 * shapes and the columns mirror right to left.
 */
@Singleton
class PdfReportExporter @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) {
    /**
     * Writes the report under a fresh name in the `exports/` cache and returns a shareable
     * URI. [title] and [profileName] are as the screen shows them; [categoryLabel] turns a
     * stored category name into the one the user reads.
     */
    suspend fun export(
        review: PeriodReview,
        title: String,
        profileName: String,
        categoryLabel: (String) -> String,
    ): Uri = withContext(Dispatchers.IO) {
        val dir = File(appContext.cacheDir, DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()
        // Bounded by age, not by overwriting: a file just shared may still be read (see ShareCardExporter).
        dir.listFiles()?.filter { it.name.endsWith(".pdf") && now - it.lastModified() > MAX_AGE_MS }?.forEach { it.delete() }

        val file = File.createTempFile("pennywise-report-", ".pdf", dir)
        val document = PdfDocument()
        try {
            Renderer(appContext.inAppLanguage(), categoryLabel).render(document, review, title, profileName)
            FileOutputStream(file).use { document.writeTo(it) }
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            document.close()
        }
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
    }

    private class Renderer(private val context: Context, private val categoryLabel: (String) -> String) {
        private val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        private val direction = if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR

        private val title = paint(20f, INK, bold = true)
        private val heading = paint(14f, INK, bold = true)
        private val body = paint(10f, INK)
        private val bodyBold = paint(10f, INK, bold = true)
        private val label = paint(8f, MUTED)
        private val figure = paint(13f, INK, bold = true)
        private val rule = Paint().apply { color = RULE; strokeWidth = 0.5f }
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BAR }

        fun render(document: PdfDocument, review: PeriodReview, heading: String, profileName: String) {
            val blocks = header(review, heading, profileName) +
                review.currencies.flatMap(::currencySection) +
                coverage(review)
            val starts = pageStarts(blocks, CONTENT_BOTTOM - MARGIN)
            starts.forEachIndexed { index, first ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                var top = MARGIN
                for (block in blocks.subList(first, starts.getOrElse(index + 1) { blocks.size })) {
                    block.draw(page.canvas, top)
                    top += block.height
                }
                footer(page.canvas, index + 1, starts.size)
                document.finishPage(page)
            }
        }

        private fun header(review: PeriodReview, heading: String, profileName: String): List<ReportBlock> {
            val dates = AppDates.dateFormatter()
            return listOf(
                text(context.getString(R.string.review_pdf_title), label),
                text(heading, title, after = 4f),
                text("${review.range.start.format(dates)} – ${review.range.end.format(dates)}", body),
                text(context.getString(R.string.review_pdf_profile, profileName), body),
                text(context.getString(R.string.review_pdf_created, LocalDate.now().format(dates)), label, after = 8f),
                ReportBlock(1f) { top -> drawLine(MARGIN, top, PAGE_WIDTH - MARGIN, top, rule) },
            )
        }

        private fun currencySection(review: CurrencyReview): List<ReportBlock> {
            val currency = review.currency
            val net = review.income - review.spent
            val blocks = mutableListOf(
                ReportBlock(SECTION_GAP),
                text(currency, heading, after = 6f, keepWithNext = true),
                figures(
                    listOf(
                        context.getString(R.string.review_income) to money(review.income, currency),
                        context.getString(R.string.review_spent) to money(review.spent, currency),
                        context.getString(R.string.review_pdf_net) to
                            Money(net.abs(), currency).format(if (net.signum() < 0) "−" else "", maskIfHidden = false),
                        context.getString(R.string.review_transactions) to review.transactionCount.toString(),
                    )
                ),
            )
            if (review.months.isNotEmpty()) {
                blocks += text(context.getString(R.string.review_by_month), bodyBold, before = 12f, after = 4f, keepWithNext = true)
                blocks += monthBars(review)
            }
            if (review.categories.isNotEmpty()) {
                // A year's previous is the year before; either way only when it compares fairly.
                val compared = review.previousSpent != null
                val columns = if (compared) CATEGORY_COLUMNS else NAME_AMOUNT_COLUMNS
                blocks += text(context.getString(R.string.review_pdf_categories), bodyBold, before = 12f, after = 2f, keepWithNext = true)
                blocks += row(
                    columns,
                    listOfNotNull(
                        context.getString(R.string.review_pdf_category),
                        context.getString(R.string.review_pdf_amount),
                        context.getString(R.string.review_pdf_change).takeIf { compared },
                    ),
                    label,
                    keepWithNext = true,
                )
                review.categories.sortedByDescending { it.amount }.forEach { line ->
                    val delta = line.amount - (line.previous ?: line.amount)
                    blocks += row(
                        columns,
                        listOfNotNull(
                            categoryLabel(line.name),
                            money(line.amount, currency),
                            if (!compared) null
                            else if (delta.signum() == 0) ""
                            else Money(delta.abs(), currency).format(if (delta.signum() > 0) "+" else "−", maskIfHidden = false),
                        ),
                        body,
                    )
                }
            }
            if (review.merchants.isNotEmpty()) {
                blocks += text(context.getString(R.string.review_pdf_merchants), bodyBold, before = 12f, after = 2f, keepWithNext = true)
                blocks += row(
                    NAME_AMOUNT_COLUMNS,
                    listOf(context.getString(R.string.review_pdf_merchant), context.getString(R.string.review_pdf_amount)),
                    label,
                    keepWithNext = true,
                )
                review.merchants.forEach { blocks += row(NAME_AMOUNT_COLUMNS, listOf(it.name, money(it.amount, currency)), body) }
            }
            return blocks
        }

        private fun coverage(review: PeriodReview): List<ReportBlock> {
            val first = review.firstRecorded
            val last = review.lastRecorded
            val note = if (review.range.kind == ReviewKind.YEAR && first != null && last != null) {
                context.getString(
                    R.string.review_coverage_span,
                    first.format(AppDates.dayMonthFormatter()),
                    last.format(AppDates.dayMonthFormatter()),
                )
            } else {
                context.getString(R.string.review_coverage)
            }
            return listOf(ReportBlock(SECTION_GAP), text(note, label))
        }

        /** Label over value, in equal columns across the width. */
        private fun figures(items: List<Pair<String, String>>): ReportBlock {
            val width = CONTENT_WIDTH / items.size
            val cells = items.mapIndexed { index, (name, value) ->
                Triple(cellX(start(index * width, width)), layout(name, label, width - CELL_GAP), layout(value, figure, width - CELL_GAP))
            }
            val height = cells.maxOf { (_, name, value) -> name.height + value.height }.toFloat()
            return ReportBlock(height) { top ->
                cells.forEach { (x, name, value) ->
                    draw(name, x, top)
                    draw(value, x, top + name.height)
                }
            }
        }

        /** One table row; [columns] are fractions of the width, the first holding a name, the rest amounts. */
        private fun row(columns: List<Float>, cells: List<String>, paint: TextPaint, keepWithNext: Boolean = false): ReportBlock {
            var offset = 0f
            val laid = cells.mapIndexed { index, text ->
                val width = columns[index] * CONTENT_WIDTH
                val x = cellX(start(offset, width))
                offset += width
                val align = if (index == 0) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
                x to layout(text, paint, width - CELL_GAP, align, maxLines = 2)
            }
            val height = laid.maxOf { it.second.height } + ROW_PADDING * 2
            return ReportBlock(height, keepWithNext) { top ->
                laid.forEach { (x, layout) -> draw(layout, x, top + ROW_PADDING) }
                drawLine(MARGIN, top + height, PAGE_WIDTH - MARGIN, top + height, rule)
            }
        }

        /** One bar per month in reading order, the busiest full height, an empty month a flat sliver. */
        private fun monthBars(review: CurrencyReview): ReportBlock {
            val months = review.months
            val highest = months.maxOf { it.spent }
            val slot = CONTENT_WIDTH / months.size
            val monthNames = AppDates.pattern("MMM")
            val labels = months.map { layout(it.month.format(monthNames), label, slot, Layout.Alignment.ALIGN_CENTER, maxLines = 1) }
            val labelHeight = labels.maxOf { it.height }.toFloat()
            return ReportBlock(BAR_HEIGHT + 4f + labelHeight) { top ->
                months.forEachIndexed { index, point ->
                    val x = start(index * slot, slot)
                    val fraction = if (highest.signum() > 0) point.spent.toFloat() / highest.toFloat() else 0f
                    val inset = slot * 0.2f
                    drawRect(
                        x + inset, top + BAR_HEIGHT * (1f - fraction.coerceAtLeast(MIN_BAR_FRACTION)),
                        x + slot - inset, top + BAR_HEIGHT, bar,
                    )
                    draw(labels[index], x, top + BAR_HEIGHT + 4f)
                }
            }
        }

        private fun footer(canvas: Canvas, page: Int, pages: Int) {
            val text = layout(context.getString(R.string.review_pdf_page, page, pages), label, CONTENT_WIDTH, Layout.Alignment.ALIGN_CENTER)
            canvas.drawLine(MARGIN, CONTENT_BOTTOM + 8f, PAGE_WIDTH - MARGIN, CONTENT_BOTTOM + 8f, rule)
            canvas.draw(text, MARGIN, CONTENT_BOTTOM + 14f)
        }

        private fun text(
            value: String,
            paint: TextPaint,
            before: Float = 0f,
            after: Float = 2f,
            keepWithNext: Boolean = false,
        ): ReportBlock {
            val laid = layout(value, paint, CONTENT_WIDTH)
            return ReportBlock(before + laid.height + after, keepWithNext) { top -> draw(laid, MARGIN, top + before) }
        }

        /** The left edge of a column [offset] from the start of the line: mirrored when the page reads right to left. */
        private fun start(offset: Float, width: Float): Float =
            if (rtl) PAGE_WIDTH - MARGIN - offset - width else MARGIN + offset

        /** A cell's text is [CELL_GAP] narrower than its column; the gap goes on the column's end side. */
        private fun cellX(columnLeft: Float): Float = if (rtl) columnLeft + CELL_GAP else columnLeft

        private fun money(amount: BigDecimal, currency: String): String =
            CurrencyFormatter.formatCurrency(amount, currency, maskIfHidden = false)

        private fun layout(
            text: String,
            paint: TextPaint,
            width: Float,
            align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
            maxLines: Int = Int.MAX_VALUE,
        ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setTextDirection(direction)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        private fun Canvas.draw(layout: StaticLayout, x: Float, y: Float) {
            save()
            translate(x, y)
            layout.draw(this)
            restore()
        }

        private fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private companion object {
        /** The FileProvider's `exports/` cache path, shared with CSV export. */
        const val DIR = "exports"
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000

        // A4 in PostScript points.
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        /** Where content stops, leaving room for the page footer. */
        const val CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN - 20f

        const val SECTION_GAP = 18f
        const val ROW_PADDING = 4f
        const val CELL_GAP = 8f
        const val BAR_HEIGHT = 90f
        const val MIN_BAR_FRACTION = 0.02f

        val CATEGORY_COLUMNS = listOf(0.5f, 0.25f, 0.25f)
        val NAME_AMOUNT_COLUMNS = listOf(0.7f, 0.3f)

        // Printed on white, whatever the app's theme.
        val INK = Color.rgb(0x1F, 0x1F, 0x1F)
        val MUTED = Color.rgb(0x5F, 0x63, 0x68)
        val RULE = Color.rgb(0xDA, 0xDC, 0xE0)
        val BAR = Color.rgb(0x3C, 0x5A, 0x99)
    }
}
