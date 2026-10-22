package java.text

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.decomposedStringWithCompatibilityMapping
import platform.Foundation.precomposedStringWithCanonicalMapping
import platform.Foundation.precomposedStringWithCompatibilityMapping

/** iOS stand-in for `java.text.Normalizer`, backed by Foundation's Unicode normalization. */
object Normalizer {
    enum class Form { NFD, NFC, NFKD, NFKC }

    @Suppress("CAST_NEVER_SUCCEEDS")
    fun normalize(src: CharSequence, form: Form): String {
        val ns = src.toString() as NSString
        return when (form) {
            Form.NFD -> ns.decomposedStringWithCanonicalMapping
            Form.NFC -> ns.precomposedStringWithCanonicalMapping
            Form.NFKD -> ns.decomposedStringWithCompatibilityMapping
            Form.NFKC -> ns.precomposedStringWithCompatibilityMapping
        }
    }
}
