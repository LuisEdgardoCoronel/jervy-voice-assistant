package com.luiscoronel.halley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoskResultParserTest {
    @Test
    fun `lee el resultado parcial con el formato de Vosk`() {
        assertEquals("hailey", VoskResultParser.text("{\n  \"partial\" : \"hailey\"\n}"))
    }

    @Test
    fun `lee el resultado final`() {
        assertEquals("hey li", VoskResultParser.text("{\n  \"text\" : \"hey li\"\n}"))
    }

    @Test
    fun `sin texto o con JSON inesperado devuelve vacio`() {
        assertEquals("", VoskResultParser.text("{\n  \"partial\" : \"\"\n}"))
        assertEquals("", VoskResultParser.text("{}"))
        assertEquals("", VoskResultParser.text(""))
    }

    @Test
    fun `encuentra la palabra clave de una palabra`() {
        assertEquals("hailey", VoskResultParser.findKeyword("hailey", KEYWORDS))
        assertEquals("halley", VoskResultParser.findKeyword("[unk] halley", KEYWORDS))
    }

    @Test
    fun `encuentra la palabra clave de dos palabras, tambien entre unk`() {
        assertEquals("hey lee", VoskResultParser.findKeyword("hey lee", KEYWORDS))
        assertEquals("hey li", VoskResultParser.findKeyword("[unk] hey [unk] li [unk]", KEYWORDS))
    }

    @Test
    fun `solo unk o vacio no es deteccion`() {
        assertNull(VoskResultParser.findKeyword("[unk] [unk]", KEYWORDS))
        assertNull(VoskResultParser.findKeyword("", KEYWORDS))
    }

    @Test
    fun `media palabra clave no es deteccion`() {
        assertNull(VoskResultParser.findKeyword("hey", KEYWORDS))
        assertNull(VoskResultParser.findKeyword("li hey", KEYWORDS))
    }

    @Test
    fun `no confunde palabras que contienen la clave`() {
        assertNull(VoskResultParser.findKeyword("haileys", KEYWORDS))
        assertNull(VoskResultParser.findKeyword("hey lima", KEYWORDS))
    }

    @Test
    fun `lee las palabras del final con confianza y tiempos`() {
        val json =
            """
            {
              "result" : [{
                  "conf" : 0.873000,
                  "end" : 2.310000,
                  "start" : 1.740000,
                  "word" : "morgana"
                }, {
                  "conf" : 1.000000,
                  "end" : 2.900000,
                  "start" : 2.400000,
                  "word" : "mañana"
                }],
              "text" : "morgana mañana"
            }
            """.trimIndent()
        assertEquals(
            listOf(
                VoskResultParser.Word("morgana", 0.873f, 1.74f, 2.31f),
                VoskResultParser.Word("mañana", 1f, 2.4f, 2.9f),
            ),
            VoskResultParser.words(json),
        )
        assertEquals("morgana mañana", VoskResultParser.text(json))
    }

    @Test
    fun `sin arreglo result no hay palabras`() {
        assertEquals(emptyList<VoskResultParser.Word>(), VoskResultParser.words("{\n  \"text\" : \"\"\n}"))
        assertEquals(emptyList<VoskResultParser.Word>(), VoskResultParser.words(""))
    }

    @Test
    fun `ignora la palabra a la que le falta un campo`() {
        val json = """{"result" : [{"conf" : 0.5, "start" : 0.1, "word" : "morgana"}], "text" : "morgana"}"""
        assertEquals(emptyList<VoskResultParser.Word>(), VoskResultParser.words(json))
    }

    @Test
    fun `acepta el orden de campos que sea`() {
        val json = """{"result" : [{"word" : "gana", "start" : 0.1, "end" : 0.4, "conf" : 0.25}]}"""
        assertEquals(listOf(VoskResultParser.Word("gana", 0.25f, 0.1f, 0.4f)), VoskResultParser.words(json))
    }

    private companion object {
        val KEYWORDS = listOf("hailey", "haley", "hayley", "halley", "hey li", "hey lee", "hey ley")
    }
}
