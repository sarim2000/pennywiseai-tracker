package com.pennywiseai.tracker.ui.screens.rules

import com.pennywiseai.tracker.domain.model.rule.LogicalOperator.AND
import com.pennywiseai.tracker.domain.model.rule.LogicalOperator.OR
import org.junit.Assert.assertEquals
import org.junit.Test

class RulePreviewTest {

    private fun join(vararg ops: com.pennywiseai.tracker.domain.model.rule.LogicalOperator) =
        joinPreviewConditions(
            texts = listOf("A", "B", "C", "D").take(ops.size),
            operators = ops.toList(),
            andFormat = "%1\$s AND %2\$s",
            orFormat = "%1\$s OR %2\$s"
        )

    @Test
    fun `each condition uses its own connector (#843)`() {
        assertEquals("A OR B", join(AND, OR))
        assertEquals("A AND B AND C", join(AND, AND, AND))
    }

    @Test
    fun `a change of connector brackets what came before, matching the engine`() {
        assertEquals("(A OR B) AND C", join(AND, OR, AND))
        assertEquals("((A AND B) OR C) AND D", join(AND, AND, OR, AND))
    }
}
