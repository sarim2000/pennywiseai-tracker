package com.pennywiseai.tracker.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfReportPaginationTest {
    private fun blocks(vararg heights: Float) = heights.map { ReportBlock(it) }

    @Test
    fun `what fits stays on one page`() {
        assertEquals(listOf(0), pageStarts(blocks(30f, 30f, 40f), pageHeight = 100f))
    }

    @Test
    fun `a block that doesn't fit starts the next page, never split`() {
        assertEquals(listOf(0, 2, 4), pageStarts(blocks(40f, 40f, 40f, 40f, 40f), pageHeight = 100f))
    }

    @Test
    fun `a heading moves with the block after it`() {
        val heading = ReportBlock(20f, keepWithNext = true)
        val pages = pageStarts(listOf(ReportBlock(70f), heading, ReportBlock(20f)), pageHeight = 100f)
        assertEquals(listOf(0, 1), pages)
    }

    @Test
    fun `a title and a table's column names move together with the first row`() {
        val title = ReportBlock(20f, keepWithNext = true)
        val columns = ReportBlock(20f, keepWithNext = true)
        val pages = pageStarts(listOf(ReportBlock(50f), title, columns, ReportBlock(20f)), pageHeight = 100f)
        assertEquals(listOf(0, 1), pages)
    }

    @Test
    fun `an oversized block gets a page to itself, and no page starts empty`() {
        assertEquals(listOf(0, 1, 2), pageStarts(blocks(10f, 250f, 10f), pageHeight = 100f))
        assertEquals(listOf(0), pageStarts(blocks(250f), pageHeight = 100f))
    }

    @Test
    fun `an empty report is still one page`() {
        assertEquals(listOf(0), pageStarts(emptyList(), pageHeight = 100f))
    }
}
