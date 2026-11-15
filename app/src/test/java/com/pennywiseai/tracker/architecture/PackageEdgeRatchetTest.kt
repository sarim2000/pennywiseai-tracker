package com.pennywiseai.tracker.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ratchet on the top-level package graph. It finds every directed edge between
 * `com.pennywiseai.tracker` packages at depth 1 (`data -> worker`, `ui -> presentation`,
 * …) in the production sources that `scripts/arch/package-deps.sh` reads, and fails
 * when an edge is missing from `config/architecture/package-edges.txt`.
 *
 * Unlike `ArchitectureTest` it also counts fully qualified names in code, so writing
 * `com.pennywiseai.tracker.worker.Foo` inline doesn't hide an edge. Comments and
 * string literals are skipped; they don't make one package depend on another.
 *
 * Allowlist entries that no longer match an edge are printed, not failed on, so
 * one PR's cleanup never breaks another PR that lands in a different order.
 * Delete them when you see them. docs/architecture.md has the details.
 */
class PackageEdgeRatchetTest {

    @Test
    fun `package graph has no edge outside the allowlist`() {
        val allowed = parseAllowlist(ALLOWLIST.readText())
        val current = scanSources()

        val added = current.keys - allowed
        val stale = allowed - current.keys
        if (stale.isNotEmpty()) {
            println(
                "Package edges in ${ALLOWLIST_PATH} that no longer exist. Delete them to tighten the ratchet:\n" +
                    stale.sorted().joinToString("\n") { "  $it" },
            )
        }
        assertTrue(
            "New package edges. Move the code to the right layer (docs/architecture.md); add an edge to " +
                "$ALLOWLIST_PATH only if the dependency points the right way and the PR says why:\n" +
                added.sorted().joinToString("\n") { edge -> "  $edge   (${current.getValue(edge).sorted().first()})" },
            added.isEmpty(),
        )
    }

    @Test
    fun `every source root is scanned`() {
        val files = sourceFiles().map { it.relativeTo(ROOT).invariantSeparatorsPath }
        for (root in listOf("app/src/main/", "app/src/standard/", "app/src/fdroid/", "feature/", "core/")) {
            assertTrue("No Kotlin file found under $root. Did a source root move?", files.any { it.startsWith(root) })
        }
    }

    @Test
    fun `scanner reads imports, import aliases and wildcards`() {
        val scan = fixture("imports.kt.txt")
        assertEquals("ui", scan.from)
        // MainActivity is a class in the root package. An import may wrap or share a line after `;`.
        // R, BuildConfig and the file's own package don't count.
        assertEquals(
            setOf("data", "domain", "utils", "(root)", "worker", "widget", "receiver"),
            PackageEdges.targets(scan, FIXTURE_PACKAGES),
        )
    }

    @Test
    fun `scanner reads fully qualified names, also wrapped, split by a comment or escaped`() {
        val scan = fixture("qualified-names.kt.txt")
        // `tracker.coverForLock(…)` is a root-level function, not a package.
        assertEquals(
            setOf("worker", "receiver", "(root)", "navigation", "billing", "aiaccess", "tile", "widget"),
            PackageEdges.targets(scan, FIXTURE_PACKAGES),
        )
    }

    @Test
    fun `scanner skips comments, strings and code outside the app's packages`() {
        val scan = fixture("ignored.kt.txt")
        assertEquals("(root)", scan.from)
        // Template expressions are code. In a $$-string only the $$-prefixed template is one.
        assertEquals(setOf("navigation", "domain", "utils", "data"), PackageEdges.targets(scan, FIXTURE_PACKAGES))
        assertEquals(null, PackageEdges.scan("package com.example.other\nimport com.pennywiseai.tracker.data.Repo"))
    }

    @Test
    fun `allowlist parser rejects malformed lines`() {
        assertEquals(setOf("data -> domain", "(root) -> ui"), parseAllowlist("# header\n\ndata -> domain\n(root)  ->  ui\n"))
        val error = runCatching { parseAllowlist("data domain\n") }.exceptionOrNull()
        assertTrue("Expected a malformed-line error, got $error", error is IllegalArgumentException)
    }

    /** Reads the allowlist: one `from -> to` per line, `#` comments and blank lines ignored. */
    private fun parseAllowlist(text: String): Set<String> = text.lines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val parts = line.split("->").map { it.trim() }
            require(parts.size == 2 && parts.all { PACKAGE_KEY.matches(it) }) {
                "Malformed line in $ALLOWLIST_PATH: '$line'. Expected 'from -> to'."
            }
            "${parts[0]} -> ${parts[1]}"
        }
        .toSet()

    /** Edge (`from -> to`) to the files that create it. */
    private fun scanSources(): Map<String, List<String>> {
        val scans = sourceFiles().mapNotNull { file -> PackageEdges.scan(file.readText())?.let { file to it } }
        val packages = scans.map { (_, scan) -> scan.from }.toSet()
        val edges = mutableMapOf<String, MutableList<String>>()
        for ((file, scan) in scans) {
            for (to in PackageEdges.targets(scan, packages)) {
                edges.getOrPut("${scan.from} -> $to") { mutableListOf() } += file.relativeTo(ROOT).invariantSeparatorsPath
            }
        }
        return edges
    }

    private fun fixture(name: String): PackageEdges.Scan {
        val text = checkNotNull(javaClass.getResource("/architecture/package-edges/$name")) { "Missing fixture $name" }.readText()
        return checkNotNull(PackageEdges.scan(text)) { "$name has no app package line" }
    }

    private companion object {
        const val ALLOWLIST_PATH = "config/architecture/package-edges.txt"

        val PACKAGE_KEY = Regex("""\(root\)|[a-z][A-Za-z0-9_]*""")

        /** Gradle runs tests from the module dir; an IDE may run them from the root. */
        val ROOT: File = (if (File("settings.gradle.kts").isFile) File(".") else File("..")).canonicalFile

        val ALLOWLIST = File(ROOT, ALLOWLIST_PATH)

        /** The packages the fixtures' sources "declare". */
        val FIXTURE_PACKAGES = setOf(
            "aiaccess", "backup", "billing", "data", "di", "domain", "navigation", "presentation",
            "receiver", "tile", "ui", "utils", "widget", "worker",
        )

        /** The same roots as scripts/arch/package-deps.sh: :app's main and flavor sources, and every feature and core module's main sources. */
        fun sourceFiles(): List<File> {
            val roots = listOf("app/src/main", "app/src/standard", "app/src/fdroid").map { File(ROOT, it) } +
                listOf("feature", "core").flatMap { group ->
                    File(ROOT, group).listFiles().orEmpty().map { File(it, "src/main") }
                }
            return roots.filter { it.isDirectory }
                .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
                .sortedBy { it.path }
        }
    }
}

/**
 * Finds a Kotlin file's package and the other top-level `com.pennywiseai.tracker` packages it
 * uses. A package key is the first segment after `com.pennywiseai.tracker`, or `(root)` for the
 * root package. `R` and `BuildConfig` are generated and don't count.
 *
 * A name's first segment is a package only if some scanned file declares that package;
 * otherwise it's a class, function or property in the root package. A name alone can't
 * tell `tracker.utils.format()` from `tracker.rootFunction()`.
 */
internal object PackageEdges {
    private const val APP = "com.pennywiseai.tracker"
    private const val ROOT_KEY = "(root)"
    private val PACKAGE = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
    private val IMPORT = Regex("""^\s*import\s+([\w.*]+)""", RegexOption.MULTILINE)
    private val HEADER_LINE = Regex("""^\s*(package|import)\s.*$""", RegexOption.MULTILINE)
    // Not after a member-access dot (`holder.com…`), but after a range's `..`.
    private val QUALIFIED = Regex("""(?:(?<=\.\.)|(?<![\w.`]))com\.pennywiseai\.tracker\.(\w+)""")

    /** A backticked plain identifier, which names the same thing without its backticks. */
    private val ESCAPED_IDENTIFIER = Regex("""`(\w+)`""")
    private val SPACED_DOT = Regex("""\s*\.\s*""")
    private val GENERATED = setOf("R", "BuildConfig")

    /** A file's package key and the first segment after `com.pennywiseai.tracker` of every name it uses. */
    class Scan(val from: String, val firstSegments: Set<String>)

    /** Null when the file isn't in one of the app's packages. */
    fun scan(source: String): Scan? {
        // Normalize names first: a name may be split across lines or by a (stripped) comment
        // around its dots, escape a segment in backticks, or share a line with another
        // directive after a semicolon.
        val code = stripCommentsAndStrings(source)
            .replace(';', '\n')
            .replace(ESCAPED_IDENTIFIER, "$1")
            .replace(SPACED_DOT, ".")
        val pkg = PACKAGE.find(code)?.groupValues?.get(1) ?: return null
        val from = when {
            pkg == APP -> ROOT_KEY
            pkg.startsWith("$APP.") -> pkg.removePrefix("$APP.").substringBefore('.')
            else -> return null
        }
        val used = mutableSetOf<String>()
        IMPORT.findAll(code).forEach { match ->
            // An alias comes after the name, so the regex leaves it out.
            val name = match.groupValues[1]
            if (name.startsWith("$APP.")) used += name.removePrefix("$APP.").substringBefore('.')
        }
        QUALIFIED.findAll(code.replace(HEADER_LINE, "")).forEach { used += it.groupValues[1] }
        return Scan(from, used - GENERATED)
    }

    /** The package keys a scan points at, given every package the scanned sources declare. */
    fun targets(scan: Scan, packages: Set<String>): Set<String> =
        scan.firstSegments.map { if (it in packages) it else ROOT_KEY }.toSet() - scan.from

    /**
     * Blanks out comments, string and char literals (keeping line breaks), but keeps the code
     * inside string templates. Good enough for import and name scanning, not a Kotlin lexer.
     */
    fun stripCommentsAndStrings(source: String): String = Stripper(source).run()

    private class Stripper(private val src: String) {
        private val out = StringBuilder(src.length)
        private var i = 0

        fun run(): String {
            code(inTemplate = false)
            return out.toString()
        }

        private fun at(token: String) = src.startsWith(token, i)

        private fun code(inTemplate: Boolean) {
            var braces = 0
            while (i < src.length) {
                val c = src[i]
                when {
                    at("//") -> while (i < src.length && src[i] != '\n') i++
                    at("/*") -> blockComment()
                    at("\"\"\"") -> {
                        val opener = templateOpener()
                        i += 3
                        string(raw = true, opener)
                    }
                    c == '"' -> {
                        val opener = templateOpener()
                        i++
                        string(raw = false, opener)
                    }
                    c == '\'' -> charLiteral()
                    c == '`' -> {
                        val end = src.indexOf('`', i + 1).let { if (it < 0) src.length else it + 1 }
                        out.append(src, i, end)
                        i = end
                    }
                    inTemplate && c == '{' -> {
                        braces++
                        out.append(c)
                        i++
                    }
                    inTemplate && c == '}' -> {
                        i++
                        if (braces == 0) return
                        braces--
                        out.append(c)
                    }
                    else -> {
                        out.append(c)
                        i++
                    }
                }
            }
        }

        /** Kotlin block comments nest. */
        private fun blockComment() {
            var depth = 0
            while (i < src.length) {
                when {
                    at("/*") -> {
                        depth++
                        i += 2
                    }
                    at("*/") -> {
                        depth--
                        i += 2
                        if (depth == 0) break
                    }
                    else -> {
                        if (src[i] == '\n') out.append('\n')
                        i++
                    }
                }
            }
            out.append(' ')
        }

        /**
         * What opens a template in the string that starts at [i]: a dollar sign and a brace,
         * or in a multi-dollar string (two dollar signs before the quote) as many dollar
         * signs as its prefix has. Fewer are plain text.
         */
        private fun templateOpener(): String {
            var dollars = 0
            while (i - dollars - 1 >= 0 && src[i - dollars - 1] == '$') dollars++
            return "$".repeat(maxOf(dollars, 1)) + "{"
        }

        private fun string(raw: Boolean, templateOpener: String) {
            out.append(' ')
            while (i < src.length) {
                val c = src[i]
                when {
                    raw && at("\"\"\"") -> {
                        i += 3
                        while (i < src.length && src[i] == '"') i++
                        return
                    }
                    !raw && c == '"' -> {
                        i++
                        return
                    }
                    !raw && c == '\\' -> i += 2
                    !raw && c == '\n' -> return
                    at(templateOpener) -> {
                        i += templateOpener.length
                        out.append(' ')
                        code(inTemplate = true)
                        out.append(' ')
                    }
                    else -> {
                        if (c == '\n') out.append('\n')
                        i++
                    }
                }
            }
        }

        private fun charLiteral() {
            i++
            while (i < src.length && src[i] != '\'') i += if (src[i] == '\\') 2 else 1
            i++
            out.append(' ')
        }
    }
}
