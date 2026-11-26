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

    private companion object {
        val KEYWORDS = listOf("hailey", "haley", "hayley", "halley", "hey li", "hey lee", "hey ley")
    }
}
