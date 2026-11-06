package lesson09

import lesson09.dsl.*
import lesson09.legacy.check03 as old3
import lesson09.dsl.check03 as new3
import lesson09.legacy.check04 as old4
import lesson09.dsl.check04 as new4
import lesson09.legacy.check05 as old5
import lesson09.dsl.check05 as new5
import lesson09.legacy.check06 as old6
import lesson09.dsl.check06 as new6

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DslChecksTest {
    private fun verify(
        response: HttpResponse,
        old: (HttpResponse) -> Boolean,
        new: (HttpResponse) -> ValidationResult,
        vararg errors: Mismatch,
    ) {
        val result = new(response)
        assertEquals(old(response), result.isValid)
        assertEquals(errors.toList(), result.mismatches)
    }

    @Test
    fun check03MatchesLegacyForEachCondition() {
        val old = ::old3
        val new = ::new3
        val good = HttpResponse(200, mapOf("Accept" to listOf("application/json", "text/html")), null)
        verify(good, old, new)
        verify(good.copy(status = 500), old, new, Mismatch("status", "200", "500"))
        verify(good.copy(headers = mapOf("Accept" to listOf("text/html"))), old, new,
            Mismatch("headers.Accept", "\"application/json\"", "[\"text/html\"]"))
        verify(good.copy(headers = mapOf("Accept" to listOf("application/json"))), old, new,
            Mismatch("headers.Accept", "\"text/html\"", "[\"application/json\"]"))
        verify(good.copy(headers = linkedMapOf(
            "Accept" to listOf("application/json"), "accept" to listOf("text/html"),
            "X-Extra" to listOf("ignored"),
        )), old, new)
        verify(good.copy(headers = mapOf("Accept" to listOf("application/json,text/html"))), old, new,
            Mismatch("headers.Accept", "\"application/json\"", "[\"application/json,text/html\"]"),
            Mismatch("headers.Accept", "\"text/html\"", "[\"application/json,text/html\"]"))
    }

    @Test
    fun check04MatchesLegacyForEachCondition() {
        val old = ::old4
        val new = ::new4
        val good = HttpResponse(200, emptyMap(), """{"id":null,"name":"Ivanov","extra":true}""")
        verify(good, old, new)
        verify(good.copy(status = 500), old, new, Mismatch("status", "200", "500"))
        verify(good.copy(body = null), old, new, Mismatch("body.json", "valid JSON object", "<missing>"))
        verify(good.copy(body = """{"name":"Ivanov"}"""), old, new,
            Mismatch("body.json[\"id\"]", "present", "<missing>"))
        verify(good.copy(body = """{"id":1,"name":"Petrov"}"""), old, new,
            Mismatch("body.json[\"name\"]", "\"Ivanov\"", "\"Petrov\""))
        verify(good.copy(body = """{"id":1}"""), old, new,
            Mismatch("body.json[\"name\"]", "\"Ivanov\"", "<missing>"))
    }

    @Test
    fun check05MatchesLegacyForEachConditionAndType() {
        val old = ::old5
        val new = ::new5
        val good = HttpResponse(200, emptyMap(), """{"id":42,"active":true}""")
        verify(good, old, new)
        verify(good.copy(status = 500), old, new, Mismatch("status", "200", "500"))
        verify(good.copy(body = null), old, new, Mismatch("body.json", "valid JSON object", "<missing>"))
        verify(good.copy(body = """{"id":43,"active":true}"""), old, new,
            Mismatch("body.json[\"id\"]", "42", "43"))
        verify(good.copy(body = """{"id":"42","active":true}"""), old, new,
            Mismatch("body.json[\"id\"]", "42", "\"42\""))
        verify(good.copy(body = """{"id":42,"active":false}"""), old, new,
            Mismatch("body.json[\"active\"]", "true", "false"))
        verify(good.copy(body = """{"id":42,"active":"true"}"""), old, new,
            Mismatch("body.json[\"active\"]", "true", "\"true\""))
    }

    @Test
    fun check06MatchesLegacyForEachCondition() {
        val old = ::old6
        val new = ::new6
        val good = HttpResponse(422, mapOf("Content-Type" to listOf("application/json")),
            """{"error":"invalid","retry":false}""")
        verify(good, old, new)
        verify(good.copy(status = 200), old, new, Mismatch("status", "422", "200"))
        verify(good.copy(headers = emptyMap()), old, new,
            Mismatch("headers.Content-Type", "prefix application/json", "<missing>"))
        verify(good.copy(body = null), old, new, Mismatch("body.json", "valid JSON object", "<missing>"))
        verify(good.copy(body = """{"error":"other","retry":false}"""), old, new,
            Mismatch("body.json[\"error\"]", "\"invalid\"", "\"other\""))
        verify(good.copy(body = """{"error":"invalid","retry":true}"""), old, new,
            Mismatch("body.json[\"retry\"]", "false", "true"))
    }

    @Test
    fun invalidJsonProducesOneErrorPerBlockAndOtherChecksContinue() {
        for (source in listOf("", " ", "invalid", "[]", "{", "{\"id\":1,}", "{\"id\":1.5}")) {
            val reason = (lesson09.support.json.parseJsonObject(source) as
                lesson09.support.json.JsonParseResult.Failure).reason
            val response = HttpResponse(500, emptyMap(), source)
            verify(response, ::old6, ::new6,
                Mismatch("status", "422", "500"),
                Mismatch("headers.Content-Type", "prefix application/json", "<missing>"),
                Mismatch("body.json", "valid JSON object", reason))
            var entered = false
            val result = validate(response) {
                body {
                    json { entered = true }
                    json { entered = true }
                }
                status { equalTo(200) }
            }
            assertEquals(false, entered)
            assertEquals(listOf(
                Mismatch("body.json", "valid JSON object", reason),
                Mismatch("body.json", "valid JSON object", reason),
                Mismatch("status", "200", "500"),
            ), result.mismatches)
        }
    }
}
