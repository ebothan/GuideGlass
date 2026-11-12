package org.guideglass.core.util

/**
 * Minimal strict JSON reader (RFC 8259 subset: no comments, no trailing commas).
 * Objects become LinkedHashMap<String, Any?>, arrays List<Any?>, numbers Double.
 * The core stays free of third-party dependencies (ADR 0002); this is only used for recorded
 * route responses and for cloud answers, which must be rejected if malformed.
 */
class JsonException(msg: String) : Exception(msg)

object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw JsonException("trailing data at ${p.i}")
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun obj(v: Any?): Map<String, Any?> = v as? Map<String, Any?> ?: throw JsonException("expected object")

    @Suppress("UNCHECKED_CAST")
    fun arr(v: Any?): List<Any?> = v as? List<Any?> ?: throw JsonException("expected array")

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i] in " \t\r\n") i++ }
        fun value(): Any? {
            ws()
            if (i >= s.length) throw JsonException("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw JsonException("bad char '$c' at $i")
            }
        }
        fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) throw JsonException("bad literal at $i")
            i += w.length; return v
        }
        fun num(): Double {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            return s.substring(st, i).toDoubleOrNull() ?: throw JsonException("bad number at $st")
        }
        fun str(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw JsonException("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw JsonException("bad escape")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw JsonException("bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> throw JsonException("bad escape \\$e")
                        }
                    }
                    else -> { if (c < ' ') throw JsonException("control char in string"); sb.append(c) }
                }
            }
        }
        fun obj(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') throw JsonException("expected key at $i")
                val k = str()
                if (k in m) throw JsonException("duplicate key $k")
                ws(); if (i >= s.length || s[i] != ':') throw JsonException("expected : at $i"); i++
                m[k] = value()
                ws()
                if (i >= s.length) throw JsonException("unterminated object")
                when (s[i++]) { ',' -> continue; '}' -> return m; else -> throw JsonException("expected , or } at ${i - 1}") }
            }
        }
        fun arr(): List<Any?> {
            i++
            val l = mutableListOf<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                l += value()
                ws()
                if (i >= s.length) throw JsonException("unterminated array")
                when (s[i++]) { ',' -> continue; ']' -> return l; else -> throw JsonException("expected , or ] at ${i - 1}") }
            }
        }
    }
}
