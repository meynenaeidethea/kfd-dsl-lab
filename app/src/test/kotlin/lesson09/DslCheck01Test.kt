package lesson09

import lesson09.dsl.Mismatch
import lesson09.dsl.ResponseScope
import lesson09.dsl.check01
import lesson09.dsl.validate
import lesson09.legacy.check01 as legacyCheck01
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DslCheck01Test {
    @Test
    fun matchesLegacyAndReportsEveryViolationInOrder() {
        val cases = listOf(
            HttpResponse(204, emptyMap(), null) to emptyList(),
            HttpResponse(204, emptyMap(), "") to emptyList(),
            HttpResponse(200, emptyMap(), null) to listOf(Mismatch("status", "204", "200")),
            HttpResponse(204, emptyMap(), " ") to listOf(Mismatch("body", "absent or empty", " ")),
            HttpResponse(204, emptyMap(), "content") to listOf(Mismatch("body", "absent or empty", "content")),
            HttpResponse(500, emptyMap(), "content") to listOf(
                Mismatch("status", "204", "500"),
                Mismatch("body", "absent or empty", "content"),
            ),
        )

        for ((response, expected) in cases) {
            val result = check01(response)
            assertEquals(legacyCheck01(response), result.isValid)
            assertEquals(expected, result.mismatches)
        }
    }

    @Test
    fun repeatedOperationsKeepDuplicateMismatches() {
        val result = validate(HttpResponse(500, emptyMap(), null)) {
            status {
                equalTo(204)
                equalTo(204)
            }
        }
        assertEquals(List(2) { Mismatch("status", "204", "500") }, result.mismatches)
    }

    @Test
    fun reusedBlockProducesIndependentResultsWithoutChangingInput() {
        val block: ResponseScope.() -> Unit = {
            status { equalTo(204) }
            body { absentOrEmpty() }
        }
        val good = HttpResponse(204, linkedMapOf("X-Trace" to listOf("trace")), null)
        val bad = good.copy(status = 500)
        val first = validate(good, block)
        val second = validate(bad, block)
        val third = validate(good, block)

        assertTrue(first.isValid)
        assertTrue(third.isValid)
        assertEquals(listOf(Mismatch("status", "204", "500")), second.mismatches)
        assertEquals(HttpResponse(204, mapOf("X-Trace" to listOf("trace")), null), good)
        assertEquals(good.copy(status = 500), bad)
    }

    @Test
    fun explicitOuterReceiverIsAvailableInsideBody() {
        val result = validate(HttpResponse(500, emptyMap(), null)) {
            body {
                this@validate.status { equalTo(204) }
            }
        }
        assertEquals(listOf(Mismatch("status", "204", "500")), result.mismatches)
    }
}
