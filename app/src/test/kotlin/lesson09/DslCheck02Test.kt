package lesson09

import lesson09.dsl.Mismatch
import lesson09.dsl.check02
import lesson09.legacy.check02 as legacyCheck02
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DslCheck02Test {
    @Test
    fun matchesLegacyAndReportsEachCondition() {
        val good = HttpResponse(200, mapOf("Content-Type" to listOf("application/json")), "invalid")
        val cases = listOf(
            good to emptyList(),
            good.copy(status = 500) to listOf(Mismatch("status", "200", "500")),
            good.copy(headers = emptyMap()) to listOf(
                Mismatch("headers.Content-Type", "prefix application/json", "<missing>")
            ),
            good.copy(body = null) to listOf(Mismatch("body", "non-empty", "<missing>")),
            good.copy(body = "") to listOf(Mismatch("body", "non-empty", "")),
            good.copy(body = " ") to emptyList(),
            good.copy(status = 500, headers = emptyMap(), body = null) to listOf(
                Mismatch("status", "200", "500"),
                Mismatch("headers.Content-Type", "prefix application/json", "<missing>"),
                Mismatch("body", "non-empty", "<missing>"),
            ),
        )
        for ((response, expected) in cases) {
            val result = check02(response)
            assertEquals(legacyCheck02(response), result.isValid)
            assertEquals(expected, result.mismatches)
        }
    }

    @Test
    fun headerNamesIgnoreCaseButValuesKeepCaseAndWhitespace() {
        val cases: List<Pair<Map<String, List<String>>, List<Mismatch>>> = listOf(
            mapOf("content-type" to listOf("application/json; charset=utf-8")) to emptyList(),
            linkedMapOf(
                "Content-Type" to listOf("text/plain"),
                "CONTENT-TYPE" to listOf("application/jsonp"),
            ) to emptyList(),
            mapOf("Content-Type" to emptyList<String>()) to listOf(
                Mismatch("headers.Content-Type", "prefix application/json", "[]")
            ),
            mapOf("Content-Type" to listOf("")) to listOf(
                Mismatch("headers.Content-Type", "prefix application/json", "[\"\"]")
            ),
            linkedMapOf(
                "Content-Type" to listOf("Application/json"),
                "content-type" to listOf(" application/json"),
            ) to listOf(
                Mismatch("headers.Content-Type", "prefix application/json", "[\"Application/json\", \" application/json\"]")
            ),
        )
        for ((headers, expected) in cases) {
            val response = HttpResponse(200, headers, "content")
            val result = check02(response)
            assertEquals(legacyCheck02(response), result.isValid)
            assertEquals(expected, result.mismatches)
        }
    }
}
