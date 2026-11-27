package com.astracode.editor

/**
 * Small strict JSON codec for the LSP transport.
 *
 * It deliberately supports only JSON values used by JSON-RPC. Parsing is
 * bounded by payload length, nesting depth and collection item count.
 */
internal object LspJsonCodec {
    private const val MAX_JSON_CHARS = 4 * 1024 * 1024
    private const val MAX_DEPTH = 64
    private const val MAX_COLLECTION_ITEMS = 100_000

    fun encode(value: Any?): String {
        val output = StringBuilder()
        appendValue(output, value, 0)
        require(output.length <= MAX_JSON_CHARS) { "JSON message exceeds the configured limit" }
        return output.toString()
    }

    fun decodeObject(json: String): Map<String, Any?> {
        require(json.length <= MAX_JSON_CHARS) { "JSON message exceeds the configured limit" }
        val parser = Parser(json)
        val value = parser.parse()
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?>
            ?: throw IllegalArgumentException("JSON-RPC message must be an object")
    }

    private fun appendValue(output: StringBuilder, value: Any?, depth: Int) {
        require(depth <= MAX_DEPTH) { "JSON nesting depth exceeded" }
        when (value) {
            null -> output.append("null")
            is String -> appendString(output, value)
            is Boolean -> output.append(value)
            is Byte, is Short, is Int, is Long -> output.append(value.toString())
            is Float -> {
                require(value.isFinite()) { "JSON numbers must be finite" }
                output.append(value.toString())
            }
            is Double -> {
                require(value.isFinite()) { "JSON numbers must be finite" }
                output.append(value.toString())
            }
            is Number -> {
                val number = value.toDouble()
                require(number.isFinite()) { "JSON numbers must be finite" }
                output.append(value.toString())
            }
            is Map<*, *> -> {
                require(value.size <= MAX_COLLECTION_ITEMS) { "JSON object has too many fields" }
                output.append('{')
                value.entries.forEachIndexed { index, entry ->
                    val key = entry.key as? String
                        ?: throw IllegalArgumentException("JSON object keys must be strings")
                    if (index > 0) output.append(',')
                    appendString(output, key)
                    output.append(':')
                    appendValue(output, entry.value, depth + 1)
                }
                output.append('}')
            }
            is Iterable<*> -> {
                val items = value.toList()
                require(items.size <= MAX_COLLECTION_ITEMS) { "JSON array has too many items" }
                output.append('[')
                items.forEachIndexed { index, item ->
                    if (index > 0) output.append(',')
                    appendValue(output, item, depth + 1)
                }
                output.append(']')
            }
            is Array<*> -> {
                require(value.size <= MAX_COLLECTION_ITEMS) { "JSON array has too many items" }
                output.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) output.append(',')
                    appendValue(output, item, depth + 1)
                }
                output.append(']')
            }
            else -> throw IllegalArgumentException("Unsupported JSON value type: " + value.javaClass.name)
        }
    }

    private fun appendString(output: StringBuilder, value: String) {
        output.append('"')
        value.forEach { character ->
            when (character) {
                '"' -> output.append("\\\"")
                '\\' -> output.append("\\\\")
                '\b' -> output.append("\\b")
                '\u000C' -> output.append("\\f")
                '\n' -> output.append("\\n")
                '\r' -> output.append("\\r")
                '\t' -> output.append("\\t")
                else -> if (character.code < 0x20) {
                    output.append("\\u")
                    output.append(character.code.toString(16).padStart(4, '0'))
                } else {
                    output.append(character)
                }
            }
        }
        output.append('"')
    }

    private class Parser(private val input: String) {
        private var index = 0

        fun parse(): Any? {
            skipWhitespace()
            val value = parseValue(0)
            skipWhitespace()
            require(index == input.length) { "Unexpected content after JSON value" }
            return value
        }

        private fun parseValue(depth: Int): Any? {
            require(depth <= MAX_DEPTH) { "JSON nesting depth exceeded" }
            skipWhitespace()
            require(index < input.length) { "Unexpected end of JSON input" }
            return when (input[index]) {
                '{' -> parseObject(depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected JSON token at character " + index)
            }
        }

        private fun parseObject(depth: Int): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            val result = linkedMapOf<String, Any?>()
            if (consume('}')) return result
            while (true) {
                skipWhitespace()
                require(index < input.length && input[index] == '"') {
                    "JSON object field name must be a string"
                }
                val key = parseString()
                require(!result.containsKey(key)) { "Duplicate JSON object field: " + key }
                skipWhitespace()
                expect(':')
                result[key] = parseValue(depth)
                require(result.size <= MAX_COLLECTION_ITEMS) { "JSON object has too many fields" }
                skipWhitespace()
                if (consume('}')) return result
                expect(',')
            }
        }

        private fun parseArray(depth: Int): List<Any?> {
            expect('[')
            skipWhitespace()
            val result = mutableListOf<Any?>()
            if (consume(']')) return result
            while (true) {
                result += parseValue(depth)
                require(result.size <= MAX_COLLECTION_ITEMS) { "JSON array has too many items" }
                skipWhitespace()
                if (consume(']')) return result
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            val output = StringBuilder()
            while (index < input.length) {
                val character = input[index++]
                when {
                    character == '"' -> return output.toString()
                    character == '\\' -> {
                        require(index < input.length) { "Incomplete JSON string escape" }
                        when (val escaped = input[index++]) {
                            '"' -> output.append('"')
                            '\\' -> output.append('\\')
                            '/' -> output.append('/')
                            'b' -> output.append('\b')
                            'f' -> output.append('\u000C')
                            'n' -> output.append('\n')
                            'r' -> output.append('\r')
                            't' -> output.append('\t')
                            'u' -> output.append(parseUnicodeEscape())
                            else -> throw IllegalArgumentException("Invalid JSON string escape: " + escaped)
                        }
                    }
                    character.code < 0x20 ->
                        throw IllegalArgumentException("Unescaped control character in JSON string")
                    else -> output.append(character)
                }
            }
            throw IllegalArgumentException("Unterminated JSON string")
        }

        private fun parseUnicodeEscape(): Char {
            require(index + 4 <= input.length) { "Incomplete JSON unicode escape" }
            val digits = input.substring(index, index + 4)
            require(digits.all { it.digitToIntOrNull(16) != null }) { "Invalid JSON unicode escape" }
            index += 4
            return digits.toInt(16).toChar()
        }

        private fun parseNumber(): Number {
            val start = index
            consume('-')
            require(index < input.length) { "Incomplete JSON number" }
            if (consume('0')) {
                require(index >= input.length || input[index] !in '0'..'9') {
                    "JSON number has a leading zero"
                }
            } else {
                require(input[index] in '1'..'9') { "Invalid JSON number" }
                while (index < input.length && input[index] in '0'..'9') index++
            }
            var decimal = false
            if (consume('.')) {
                decimal = true
                require(index < input.length && input[index] in '0'..'9') {
                    "Invalid JSON fraction"
                }
                while (index < input.length && input[index] in '0'..'9') index++
            }
            if (index < input.length && (input[index] == 'e' || input[index] == 'E')) {
                decimal = true
                index++
                if (index < input.length && (input[index] == '+' || input[index] == '-')) index++
                require(index < input.length && input[index] in '0'..'9') {
                    "Invalid JSON exponent"
                }
                while (index < input.length && input[index] in '0'..'9') index++
            }
            val raw = input.substring(start, index)
            if (!decimal) raw.toLongOrNull()?.let { return it }
            return raw.toDoubleOrNull()
                ?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException("JSON number is outside the supported range")
        }

        private fun parseLiteral(token: String, value: Any?): Any? {
            require(input.regionMatches(index, token, 0, token.length)) {
                "Invalid JSON literal at character " + index
            }
            index += token.length
            return value
        }

        private fun skipWhitespace() {
            while (index < input.length && input[index] in charArrayOf(' ', '\t', '\n', '\r')) {
                index++
            }
        }

        private fun expect(character: Char) {
            require(index < input.length && input[index] == character) {
                "Expected '" + character + "' at character " + index
            }
            index++
        }

        private fun consume(character: Char): Boolean {
            if (index < input.length && input[index] == character) {
                index++
                return true
            }
            return false
        }
    }
}
