package lesson09.dsl

import lesson09.HttpResponse
import lesson09.support.json.JsonParseResult
import lesson09.support.json.parseJsonObject

data class Mismatch(val path: String, val expected: String, val actual: String)

data class ValidationResult(val mismatches: List<Mismatch>) {
    val isValid: Boolean get() = mismatches.isEmpty()

    fun describe(): String = mismatches.joinToString("\n") {
        "${it.path}: expected ${it.expected}, actual ${it.actual}"
    }
}

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class ResponseDsl

fun validate(response: HttpResponse, block: ResponseScope.() -> Unit): ValidationResult {
    val mismatches = mutableListOf<Mismatch>()
    ResponseScope(response, mismatches).block()
    return ValidationResult(mismatches.toList())
}

private fun display(value: Any?): String = when (value) {
    null -> "null"
    is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
    else -> value.toString()
}

@ResponseDsl
class ResponseScope internal constructor(
    private val response: HttpResponse,
    private val mismatches: MutableList<Mismatch>,
) {
    fun status(block: StatusScope.() -> Unit) {
        StatusScope(response.status, mismatches).block()
    }

    fun body(block: BodyScope.() -> Unit) {
        BodyScope(response.body, mismatches).block()
    }

    fun header(name: String, block: HeaderScope.() -> Unit) {
        val matchingHeaders = response.headers.entries.filter {
            it.key.equals(name, ignoreCase = true)
        }
        val values = if (matchingHeaders.isEmpty()) null else matchingHeaders.flatMap { it.value }
        HeaderScope(name, values, mismatches).block()
    }
}

@ResponseDsl
class HeaderScope internal constructor(
    private val name: String,
    private val values: List<String>?,
    private val mismatches: MutableList<Mismatch>,
) {
    fun equalTo(expected: String) {
        satisfies(display(expected)) { it == expected }
    }

    fun startsWith(prefix: String) {
        satisfies("prefix $prefix") { it.startsWith(prefix) }
    }

    fun satisfies(expectation: String, predicate: (String) -> Boolean) {
        if (values?.any(predicate) != true) {
            val actual = values?.joinToString(prefix = "[", postfix = "]") { display(it) }
                ?: "<missing>"
            mismatches.add(Mismatch("headers.$name", expectation, actual))
        }
    }
}

@ResponseDsl
class StatusScope internal constructor(
    private val actual: Int,
    private val mismatches: MutableList<Mismatch>,
) {
    fun equalTo(expected: Int) {
        if (actual != expected) {
            mismatches.add(Mismatch("status", expected.toString(), actual.toString()))
        }
    }
}

@ResponseDsl
class BodyScope internal constructor(
    private val actual: String?,
    private val mismatches: MutableList<Mismatch>,
) {
    fun nonEmpty() {
        if (actual.isNullOrEmpty()) {
            mismatches.add(Mismatch("body", "non-empty", actual ?: "<missing>"))
        }
    }

    fun absentOrEmpty() {
        if (!actual.isNullOrEmpty()) {
            mismatches.add(Mismatch("body", "absent or empty", actual))
        }
    }

    fun json(block: JsonScope.() -> Unit) {
        if (actual == null) {
            mismatches.add(Mismatch("body.json", "valid JSON object", "<missing>"))
            return
        }
        when (val parsed = parseJsonObject(actual)) {
            is JsonParseResult.Failure -> mismatches.add(
                Mismatch("body.json", "valid JSON object", parsed.reason)
            )
            is JsonParseResult.Success -> JsonScope(parsed.fields, mismatches).block()
        }
    }
}

@ResponseDsl
class JsonScope internal constructor(
    private val fields: Map<String, Any?>,
    private val mismatches: MutableList<Mismatch>,
) {
    fun field(name: String, block: FieldScope.() -> Unit) {
        FieldScope(name, fields.containsKey(name), fields[name], mismatches).block()
    }
}

@ResponseDsl
class FieldScope internal constructor(
    private val name: String,
    private val exists: Boolean,
    private val actual: Any?,
    private val mismatches: MutableList<Mismatch>,
) {
    // Bracket notation keeps field names containing dots or quotes unambiguous.
    private val path: String get() = "body.json[${display(name)}]"

    fun present() {
        if (!exists) mismatches.add(Mismatch(path, "present", "<missing>"))
    }

    fun equalTo(expected: String) = compare(expected)
    fun equalTo(expected: Long) = compare(expected)
    fun equalTo(expected: Boolean) = compare(expected)

    private fun compare(expected: Any) {
        if (!exists || actual != expected) {
            mismatches.add(Mismatch(path, display(expected), if (exists) display(actual) else "<missing>"))
        }
    }
}
