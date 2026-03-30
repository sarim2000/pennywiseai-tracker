import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun main() {
    val dateStr = "08/05/25" // example output from FederalBankParser for parseFutureDebit
    val format = "dd-MM-yyyy" // value of info.dateFormat
    try {
        println(LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(format)).isAfter(LocalDate.now()))
    } catch (e: Exception) {
        println("FAILED: ${e.message}")
    }
}
