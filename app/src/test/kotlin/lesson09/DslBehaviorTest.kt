package lesson09

import lesson09.dsl.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun nonBlank(value: String): Boolean = value.isNotBlank()

class DslBehaviorTest {
    @Test
    fun predicateFormsHaveTheSameResults() {
        val predicate: (String) -> Boolean = ::nonBlank
        for (headers in listOf(
            emptyMap(), mapOf("X-Trace" to emptyList()), mapOf("X-Trace" to listOf("")),
            mapOf("X-Trace" to listOf(" ", "trace")),
        )) {
            val response = HttpResponse(200, headers, null)
            val literal = validate(response) {
                header("X-Trace") { satisfies("non-blank") { value -> value.isNotBlank() } }
            }
            val variable = validate(response) {
                header("X-Trace") { satisfies("non-blank", predicate) }
            }
            val reference = validate(response) {
                header("X-Trace") { satisfies("non-blank", ::nonBlank) }
            }
            assertEquals(literal, variable)
            assertEquals(literal, reference)
            val values = headers["X-Trace"]
            val expected = when {
                values == null -> listOf(Mismatch("headers.X-Trace", "non-blank", "<missing>"))
                values.isEmpty() -> listOf(Mismatch("headers.X-Trace", "non-blank", "[]"))
                values == listOf("") -> listOf(Mismatch("headers.X-Trace", "non-blank", "[\"\"]"))
                else -> emptyList()
            }
            assertEquals(expected, literal.mismatches)
        }
    }

    @Test
    fun predicateIsNotCalledWithoutValues() {
        var calls = 0
        for (headers in listOf(emptyMap(), mapOf("X" to emptyList<String>()))) {
            val result = validate(HttpResponse(200, headers, null)) {
                header("X") { satisfies("value") { calls++; true } }
            }
            assertEquals(1, result.mismatches.size)
        }
        assertEquals(0, calls)
    }

    @Test
    fun missingNullAndWrongTypesAreDistinctAndFieldSelectionAloneDoesNothing() {
        val result = validate(HttpResponse(200, emptyMap(), """{"nil":null,"number":42,"flag":true}""")) {
            body {
                json {
                    field("unused") { }
                    field("nil") { present(); equalTo("null") }
                    field("missing") { present(); equalTo(42L); equalTo(42L) }
                    field("number") { equalTo("42") }
                    field("flag") { equalTo("true") }
                }
            }
        }
        assertEquals(listOf(
            Mismatch("body.json[\"nil\"]", "\"null\"", "null"),
            Mismatch("body.json[\"missing\"]", "present", "<missing>"),
            Mismatch("body.json[\"missing\"]", "42", "<missing>"),
            Mismatch("body.json[\"missing\"]", "42", "<missing>"),
            Mismatch("body.json[\"number\"]", "\"42\"", "42"),
            Mismatch("body.json[\"flag\"]", "\"true\"", "true"),
        ), result.mismatches)
    }

    @Test
    fun reusableBlockKeepsAllResultsAndInputCollectionsIndependent() {
        val block: ResponseScope.() -> Unit = {
            header("Content-Type") { equalTo("application/json") }
            body { json { field("id") { equalTo(42L) } } }
            status { equalTo(200) }
        }
        val values = mutableListOf("application/json")
        val headers = linkedMapOf("Content-Type" to values)
        val good = HttpResponse(200, headers, """{"id":42,"extra":false}""")
        val bad = HttpResponse(500, mapOf("content-type" to listOf("text/plain")), "{}")
        val first = validate(good, block)
        val second = validate(bad, block)
        val third = validate(good, block)
        assertTrue(first.isValid)
        assertTrue(third.isValid)
        assertEquals(listOf(
            Mismatch("headers.Content-Type", "\"application/json\"", "[\"text/plain\"]"),
            Mismatch("body.json[\"id\"]", "42", "<missing>"),
            Mismatch("status", "200", "500"),
        ), second.mismatches)
        assertEquals(mapOf("Content-Type" to listOf("application/json")), headers)
        assertEquals(listOf("application/json"), values)
        assertEquals("""{"id":42,"extra":false}""", good.body)
        assertEquals(HttpResponse(500, mapOf("content-type" to listOf("text/plain")), "{}"), bad)
    }

    @Test
    fun jsonRunsRegardlessOfContentTypeAndReportsNestedChecksInPlace() {
        val result = validate(HttpResponse(500, mapOf("Content-Type" to listOf("text/plain")), "{}")) {
            status { equalTo(200) }
            header("Content-Type") { startsWith("application/json") }
            body { json { field("a.b") { present() } } }
            status { equalTo(201) }
        }
        assertEquals(listOf(
            Mismatch("status", "200", "500"),
            Mismatch("headers.Content-Type", "prefix application/json", "[\"text/plain\"]"),
            Mismatch("body.json[\"a.b\"]", "present", "<missing>"),
            Mismatch("status", "201", "500"),
        ), result.mismatches)
        assertEquals("status: expected 200, actual 500", result.describe().lineSequence().first())
    }
}
