package com.example.loopmuse.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.Charset

class EmbeddedLyricsReaderTest {
    @Test fun readsKoreanId3Lyrics() {
        assertEquals("오늘도 좋은 날", read("mp3", id3Lyrics("오늘도 좋은 날")))
    }

    @Test fun readsWavId3Lyrics() {
        val tag = id3Lyrics("노래 가사")
        val wav = "RIFF".toByteArray() + le(tag.size + 12) + "WAVE".toByteArray() +
            "ID3 ".toByteArray() + le(tag.size) + tag + byteArrayOf(0)
        assertEquals("노래 가사", read("wav", wav))
    }

    private fun id3Lyrics(lyrics: String): ByteArray {
        val body = byteArrayOf(3) + "kor".toByteArray() + byteArrayOf(0) + lyrics.toByteArray()
        val frame = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).use { data ->
                data.writeBytes("USLT")
                data.writeInt(body.size)
                data.writeShort(0)
                data.write(body)
            }
        }.toByteArray()
        return "ID3".toByteArray() + byteArrayOf(3, 0, 0) + synchsafe(frame.size) + frame
    }

    @Test fun readsFlacVorbisLyrics() {
        val comment = "LYRICS=첫째 줄\n둘째 줄".toByteArray()
        val block = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).use { data ->
                data.writeInt(Integer.reverseBytes(0)) // vendor
                data.writeInt(Integer.reverseBytes(1)) // comment count
                data.writeInt(Integer.reverseBytes(comment.size))
                data.write(comment)
            }
        }.toByteArray()
        val flac = "fLaC".toByteArray() + byteArrayOf(0x84.toByte(), 0, 0, block.size.toByte()) + block
        assertEquals("첫째 줄\n둘째 줄", read("flac", flac))
    }

    @Test fun readsM4aLyrics() {
        val data = box("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + "노랫말".toByteArray())
        val m4a = box("moov", box("udta", box("meta", byteArrayOf(0, 0, 0, 0) +
            box("ilst", box("©lyr", data)))))
        assertEquals("노랫말", read("m4a", m4a))
    }

    @Test fun readsOggCommentsAndRejectsMissingLyrics() {
        val first = byteArrayOf(1) + "vorbis".toByteArray()
        val comment = "LYRICS=비 오는 날".toByteArray()
        val second = byteArrayOf(3) + "vorbis".toByteArray() + le(0) + le(1) + le(comment.size) + comment
        val page = byteArrayOf(*"OggS".toByteArray(), 0, 0) + ByteArray(20) +
            byteArrayOf(2, first.size.toByte(), second.size.toByte()) + first + second
        assertEquals("비 오는 날", read("ogg", page))
        assertEquals(null, read("mp3", ByteArray(32)))
    }

    @Test fun decodesKoreanLrcFiles() {
        val lyric = "[00:01.00]오늘도 좋은 날"
        assertEquals(lyric, decodeLocalLyrics(lyric.toByteArray(Charset.forName("EUC-KR"))))
        assertEquals(lyric, decodeLocalLyrics(lyric.toByteArray(Charsets.UTF_16)))
    }

    private fun read(extension: String, bytes: ByteArray): String? {
        val file = File.createTempFile("loopmuse-lyrics-", ".$extension")
        return try {
            file.writeBytes(bytes)
            EmbeddedLyricsReader.read(file)
        } finally {
            file.delete()
        }
    }

    private fun box(type: String, body: ByteArray): ByteArray =
        java.nio.ByteBuffer.allocate(4).putInt(body.size + 8).array() +
            type.toByteArray(Charsets.ISO_8859_1) + body

    private fun le(value: Int): ByteArray = java.nio.ByteBuffer.allocate(4)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun synchsafe(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7f).toByte(), ((value shr 14) and 0x7f).toByte(),
        ((value shr 7) and 0x7f).toByte(), (value and 0x7f).toByte()
    )
}
