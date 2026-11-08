package com.pennywiseai.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val PACKAGE = "com.pennywiseai.tracker.benchmark"
private const val TIMEOUT_MS = 30_000L

/** Fills the app through its benchmark-only receiver. Run before any Rule resets the profiles. */
internal fun seedApp() {
    val output = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        .executeShellCommand("am broadcast -f 32 -n $PACKAGE/com.pennywiseai.tracker.benchmark.BenchmarkSeedReceiver")
    check("result=-1" in output) { "Seeding failed: $output" }
}

internal fun MacrobenchmarkScope.waitForHome() {
    checkNotNull(device.wait(Until.findObject(By.desc("Transactions")), TIMEOUT_MS)) { "Home never appeared" }
    device.waitForIdle()
}

/** The tab opens on This Month, so All Time is what puts every seeded row in the list. */
internal fun MacrobenchmarkScope.openTransactions() {
    device.findObject(By.desc("Transactions")).click()
    tap("This Month")
    tap("All Time")
    device.waitForIdle()
}

private fun MacrobenchmarkScope.tap(text: String) {
    checkNotNull(device.wait(Until.findObject(By.text(text)), TIMEOUT_MS)) { "$text never appeared" }.click()
}

internal fun MacrobenchmarkScope.scrollTransactions() {
    // Only list rows have a "time · category" line, which tells the list from the chip row above it.
    val row = By.textContains(" · ")
    val list = checkNotNull(device.wait(Until.findObject(By.scrollable(true).hasDescendant(row)), TIMEOUT_MS)) {
        "Transaction list never appeared"
    }
    val before = list.findObjects(row).map { it.text }
    list.setGestureMarginPercentage(0.2f)
    repeat(4) { list.fling(Direction.DOWN) }
    device.waitForIdle()
    check(list.findObjects(row).map { it.text } != before) { "The list did not scroll" }
}
