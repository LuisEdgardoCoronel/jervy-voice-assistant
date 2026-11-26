package com.luiscoronel.jervy.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoskResultParserTest {
    @Test
    fun `lee el resultado parcial con el formato de Vosk`() {
        assertEquals("jer vi", VoskResultParser.text("{\n  \"partial\" : \"jer vi\"\n}"))
    }

    @Test
    fun `lee el resultado final`() {
        assertEquals("yer vi", VoskResultParser.text("{\n  \"text\" : \"yer vi\"\n}"))
    }

    @Test
    fun `sin texto o con JSON inesperado devuelve vacio`() {
        assertEquals("", VoskResultParser.text("{\n  \"partial\" : \"\"\n}"))
        assertEquals("", VoskResultParser.text("{}"))
        assertEquals("", VoskResultParser.text(""))
    }

    @Test
    fun `encuentra la palabra clave`() {
        assertEquals("jer vi", VoskResultParser.findKeyword("jer vi", KEYWORDS))
        assertEquals("yer bi", VoskResultParser.findKeyword("yer bi", KEYWORDS))
    }

    @Test
    fun `encuentra la palabra clave entre unk`() {
        assertEquals("jer vi", VoskResultParser.findKeyword("[unk] jer [unk] vi [unk]", KEYWORDS))
    }

    @Test
    fun `solo unk o vacio no es deteccion`() {
        assertNull(VoskResultParser.findKeyword("[unk] [unk]", KEYWORDS))
        assertNull(VoskResultParser.findKeyword("", KEYWORDS))
    }

    @Test
    fun `media palabra clave no es deteccion`() {
        assertNull(VoskResultParser.findKeyword("jer", KEYWORDS))
        assertNull(VoskResultParser.findKeyword("vi jer", KEYWORDS))
    }

    @Test
    fun `no confunde palabras que contienen la clave`() {
        assertNull(VoskResultParser.findKeyword("jervi", KEYWORDS))
        assertNull(VoskResultParser.findKeyword("ajer vino", KEYWORDS))
    }

    private companion object {
        val KEYWORDS = listOf("jer vi", "yer vi", "jer bi", "yer bi")
    }
}
