package com.pennywiseai.tracker.ui.components

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.offset
import com.pennywiseai.shared.domain.money.MinorUnits
import com.pennywiseai.tracker.domain.model.AmountInput
import kotlinx.coroutines.launch
import java.text.DecimalFormatSymbols

/**
 * Where the operator row shows for the amount fields under it: the focused one's.
 * Dialogs and sheets are windows of their own, so each takes its own host; a field
 * only uses a host in its own window, never one behind it.
 */
@Stable
internal class AmountBarHostState(val root: View) {
    var target by mutableStateOf<AmountEntry?>(null)
    var focusManager: FocusManager? = null
}

private val LocalAmountBarHost = staticCompositionLocalOf<AmountBarHostState?> { null }

@Composable
private fun rememberHostState(): AmountBarHostState {
    val root = LocalView.current.rootView
    val state = remember(root) { AmountBarHostState(root) }
    state.focusManager = LocalFocusManager.current
    return state
}

@Composable
private fun HostBar(target: AmountEntry, host: AmountBarHostState) {
    AmountKeyboardBar(
        onOperator = target::operator,
        onEquals = target::fold,
        onDone = {
            target.fold()
            host.focusManager?.clearFocus()
        }
    )
}

/**
 * The app's screens: the operator row rides on the keyboard while an amount field
 * has focus, and the screen above gives up the row's height so it covers nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmountBarScreenHost(content: @Composable () -> Unit) {
    val host = rememberHostState()
    val target = host.target
    val ime = WindowInsets.ime
    CompositionLocalProvider(LocalAmountBarHost provides host) {
        Layout(
            content = {
                Box(propagateMinConstraints = true) { content() }
                if (target != null && WindowInsets.isImeVisible) HostBar(target, host)
            }
        ) { measurables, constraints ->
            val bar = measurables.getOrNull(1)?.measure(constraints.copy(minHeight = 0))
            val barHeight = bar?.height ?: 0
            // The screen pads for the keyboard itself, from its own bottom: end it the row's height early.
            val body = measurables[0].measure(constraints.offset(vertical = -barHeight))
            val height = body.height + barHeight
            val keyboard = ime.getBottom(this)
            layout(body.width, height) {
                body.place(0, 0)
                bar?.place(0, (height - barHeight - keyboard).coerceAtLeast(0))
            }
        }
    }
}

/**
 * A bottom sheet's content, with the operator row under it while one of its amount
 * fields has focus. Pads for the keyboard itself, so the row sits on it.
 */
@Composable
fun AmountBarHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    StackedHost(modifier.imePadding(), content)
}

/**
 * An AlertDialog's text, with the operator row under it while one of its amount
 * fields has focus. The dialog keeps itself above the keyboard: no padding for it here.
 */
@Composable
fun AmountBarDialogHost(content: @Composable () -> Unit) {
    StackedHost(Modifier, content)
}

@Composable
private fun StackedHost(modifier: Modifier, content: @Composable () -> Unit) {
    val host = rememberHostState()
    val target = host.target
    CompositionLocalProvider(LocalAmountBarHost provides host) {
        Layout(
            content = {
                Box { content() }
                if (target != null) HostBar(target, host)
            },
            modifier = modifier
        ) { measurables, constraints ->
            val bar = measurables.getOrNull(1)?.measure(constraints.copy(minHeight = 0))
            val barHeight = bar?.height ?: 0
            val body = measurables[0].measure(constraints.offset(vertical = -barHeight))
            layout(maxOf(body.width, bar?.width ?: 0), body.height + barHeight) {
                body.place(0, 0)
                bar?.place(0, body.height)
            }
        }
    }
}

/**
 * An amount field that also takes a sum ("120+35"), typed with the operator row of
 * the nearest [AmountBarScreenHost], [AmountBarHost] or [AmountBarDialogHost] in its
 * window. Hand the field [state], [inputTransformation] and [modifier], and show
 * [sumHint] under it.
 *
 * The caller's amount is only ever a plain number: what the sum comes to so far, or
 * empty when it can't be an amount. So its own checks and parsing stay as they were.
 * Leaving the field (or `=`, or the check) folds the sum into its result. An amount
 * the caller sets from outside replaces what the field shows.
 */
@Stable
class AmountEntry internal constructor(text: String) {
    /** What the field shows: the amount, or the sum being typed. */
    val state = TextFieldState(text)

    // The amounts handed on that the caller hasn't handed back yet: its amount can trail the
    // typing by a frame, and that's not an amount set from outside.
    private val pending = ArrayDeque<String>()
    private var handled = text
    private var focused = false
    internal var host: AmountBarHostState? = null
    internal var amount by mutableStateOf(text)
    internal var onAmountChange: (String) -> Unit = {}
    internal var currency = ""
    internal var decimalComma = false

    private val digits get() = MinorUnits.digits(currency)

    /** Turns away a keystroke that isn't an amount or a sum, or is finer than the currency counts. */
    val inputTransformation: InputTransformation = amountInputTransformation({ digits }, { decimalComma })

    /** "= ₹155.00" or why the sum isn't an amount yet; null for a plain amount. */
    val sumHint: AmountSumHint?
        @Composable get() = amountSumHint(state.text.toString(), currency)

    /** For the field: tracks its focus, and finds the host where the field is, not where this was made. */
    val modifier: Modifier = Modifier.then(AmountFieldElement(this))

    internal fun focusChanged(isFocused: Boolean, host: AmountBarHostState?) {
        if (focused && !isFocused) fold()
        focused = isFocused
        if (isFocused) {
            this.host = host
            host?.target = this
        } else {
            detach()
        }
    }

    /** The field's text changed (typed, an operator, a fold): hand on what it comes to. */
    internal fun textChanged(text: String) {
        if (text == handled) return
        handled = text
        val result = comesTo(text)
        pending.addLast(result)
        onAmountChange(result)
    }

    // "120+" counts as 120 meanwhile, as leaving the field would make it: an optional
    // amount saved mid-sum keeps what's typed rather than reading as cleared.
    private fun comesTo(text: String): String = if (AmountInput.hasOperator(text)) {
        val finished = text.trimEnd { it in AmountInput.OPERATORS }
        AmountInput.value(finished, digits)?.takeIf { it.signum() > 0 }?.toPlainString().orEmpty()
    } else {
        text
    }

    /**
     * The caller's amount (also its first, and after a restore): unless it's one handed on from
     * here or what the field already comes to, it replaces the field's text.
     */
    internal fun amountChanged(new: String) {
        val i = pending.indexOfFirst { sameAmount(it, new) }
        if (i >= 0) {
            repeat(i + 1) { pending.removeFirst() }
            return
        }
        pending.clear()
        if (sameAmount(comesTo(state.text.toString()), new)) return
        handled = new
        state.setTextAndPlaceCursorAtEnd(new)
    }

    fun operator(op: Char) {
        state.setTextAndPlaceCursorAtEnd(AmountInput.append(state.text.toString(), op))
        textChanged(state.text.toString())
    }

    /** A finished sum becomes its result; for a field's own Done action. */
    fun fold() {
        AmountInput.fold(state.text.toString(), digits)?.let {
            state.setTextAndPlaceCursorAtEnd(it)
            textChanged(it)
        }
    }

    internal fun detach() {
        host?.let { if (it.target === this) it.target = null }
        host = null
    }

    companion object {
        internal val Saver = Saver<AmountEntry, String>(
            save = { it.state.text.toString() },
            restore = { AmountEntry(it) }
        )

        // "15" and "15.00" are the same amount: a caller may hand its amount back reformatted.
        private fun sameAmount(a: String, b: String): Boolean =
            a == b || (a.toBigDecimalOrNull()?.let { x -> b.toBigDecimalOrNull()?.let { x.compareTo(it) == 0 } } == true)
    }
}

/** Under an amount field: the sum's line while it holds one, else [otherwise]. */
fun AmountSumHint?.orSupporting(otherwise: (@Composable () -> Unit)? = null): (@Composable () -> Unit)? =
    this?.let { hint -> { Text(hint.text) } } ?: otherwise

/** An [AmountEntry] for an amount field holding [amount] in [currency]. */
@Composable
fun rememberAmountEntry(amount: String, onAmountChange: (String) -> Unit, currency: String): AmountEntry {
    val entry = rememberSaveable(saver = AmountEntry.Saver) { AmountEntry(amount) }
    val locale = LocalConfiguration.current.locales[0]
    val decimalComma = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',' }
    entry.onAmountChange = onAmountChange
    entry.currency = currency
    entry.decimalComma = decimalComma
    SideEffect { entry.amount = amount }
    LaunchedEffect(entry) {
        launch { snapshotFlow { entry.state.text.toString() }.collect(entry::textChanged) }
        snapshotFlow { entry.amount }.collect(entry::amountChanged)
    }
    return entry
}

private class AmountFieldElement(val entry: AmountEntry) : ModifierNodeElement<AmountFieldNode>() {
    override fun create() = AmountFieldNode(entry)
    override fun update(node: AmountFieldNode) {
        node.entry = entry
    }
    override fun equals(other: Any?) = other is AmountFieldElement && other.entry === entry
    override fun hashCode() = System.identityHashCode(entry)
}

private class AmountFieldNode(var entry: AmountEntry) :
    Modifier.Node(),
    FocusEventModifierNode,
    CompositionLocalConsumerModifierNode {
    override fun onFocusEvent(focusState: FocusState) {
        // Only a host in the field's own window: one behind a dialog would sit under it.
        val root = currentValueOf(LocalView).rootView
        entry.focusChanged(focusState.isFocused, currentValueOf(LocalAmountBarHost)?.takeIf { it.root === root })
    }

    override fun onDetach() {
        entry.detach()
    }
}
