package com.example.loopmuse.service

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/** Reads lyric tags only when a song is played; the music library scan stays unchanged. */
internal object EmbeddedLyricsReader {
    private const val MAX_TAG_BYTES = 4 * 1024 * 1024
    private const val MAX_LYRICS_BYTES = 100_000

    fun read(file: File): String? = runCatching {
        if (!file.isFile) return@runCatching null
        RandomAccessFile(file, "r").use { input ->
            when (file.extension.lowercase()) {
                "mp3" -> readId3(input)
                "flac" -> readFlac(input)
                "ogg" -> readOgg(input)
                "m4a" -> readMp4(input, 0, input.length(), 0)
                "wav" -> readWav(input)
                else -> null
            }
        }
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() && it.toByteArray(Charsets.UTF_8).size <= MAX_LYRICS_BYTES }

    private fun readId3(input: RandomAccessFile): String? {
        if (input.length() < 10) return null
        input.seek(0)
        val header = ByteArray(10).also(input::readFully)
        if (String(header, 0, 3, Charsets.ISO_8859_1) != "ID3") return null
        val version = header[3].toInt() and 0xff
        if (version !in 2..4) return null
        val size = synchsafe(header, 6)
        if (size <= 0 || size > MAX_TAG_BYTES || size > input.length() - 10) return null
        val tag = ByteArray(size).also(input::readFully)
        return parseId3(header, tag)
    }

    private fun parseId3(header: ByteArray, tag: ByteArray): String? {
        val version = header[3].toInt() and 0xff
        val size = tag.size
        var offset = 0
        if (header[5].toInt() and 0x40 != 0 && version >= 3 && size >= 4) {
            val extended = if (version == 4) synchsafe(tag, 0) else bigEndian(tag, 0, 4)
            offset = if (version == 4) extended else extended + 4
        }
        val frameHeader = if (version == 2) 6 else 10
        while (offset >= 0 && offset + frameHeader <= tag.size) {
            val idLength = if (version == 2) 3 else 4
            val id = String(tag, offset, idLength, Charsets.ISO_8859_1)
            if (!id.all { it.isLetterOrDigit() }) break
            val frameSize = if (version == 2) bigEndian(tag, offset + 3, 3)
                else if (version == 4) synchsafe(tag, offset + 4)
                else bigEndian(tag, offset + 4, 4)
            val start = offset + frameHeader
            if (frameSize <= 0 || frameSize > tag.size - start) break
            if (id in listOf("USLT", "ULT", "TXXX", "TXX")) {
                var body = tag.copyOfRange(start, start + frameSize)
                if (header[5].toInt() and 0x80 != 0 || version == 4 && tag[offset + 9].toInt() and 0x02 != 0) {
                    body = removeUnsynchronisation(body)
                }
                val lyric = if (id == "USLT" || id == "ULT") decodeId3Text(body, 4)
                    else decodeUserText(body)
                if (!lyric.isNullOrBlank()) return lyric
            }
            offset = start + frameSize
        }
        return null
    }

    private fun decodeUserText(body: ByteArray): String? {
        val description = decodeId3Text(body, 1, onlyDescription = true) ?: return null
        if (description.uppercase() !in setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")) return null
        return decodeId3Text(body, 1)
    }

    private fun decodeId3Text(body: ByteArray, prefix: Int, onlyDescription: Boolean = false): String? {
        if (body.size <= prefix) return null
        val encoding = body[0].toInt() and 0xff
        val doubleByte = encoding == 1 || encoding == 2
        val separator = if (doubleByte) 2 else 1
        var end = prefix
        while (end + separator <= body.size) {
            if ((0 until separator).all { body[end + it] == 0.toByte() }) break
            end += if (doubleByte) 2 else 1
        }
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return null
        }
        val from = if (onlyDescription) prefix else (end + separator).coerceAtMost(body.size)
        val to = if (onlyDescription) end else body.size
        if (to <= from || to - from > MAX_LYRICS_BYTES * 2) return null
        return String(body, from, to - from, charset).trim('\u0000', '\uFEFF', ' ', '\n', '\r')
    }

    private fun removeUnsynchronisation(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(bytes.size)
        var index = 0
        while (index < bytes.size) {
            output.write(bytes[index].toInt())
            if (bytes[index] == 0xff.toByte() && index + 1 < bytes.size && bytes[index + 1] == 0.toByte()) index++
            index++
        }
        return output.toByteArray()
    }

    private fun readFlac(input: RandomAccessFile): String? {
        if (input.length() < 8) return null
        input.seek(0)
        if (ByteArray(4).also(input::readFully).toString(Charsets.ISO_8859_1) != "fLaC") return null
        do {
            if (input.filePointer + 4 > input.length()) return null
            val marker = input.readUnsignedByte()
            val size = (input.readUnsignedByte() shl 16) or (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
            if (size > input.length() - input.filePointer) return null
            if (marker and 0x7f == 4 && size in 1..MAX_TAG_BYTES) {
                return readVorbisComments(ByteArray(size).also(input::readFully), 0)
            }
            input.seek(input.filePointer + size)
        } while (marker and 0x80 == 0)
        return null
    }

    private fun readOgg(input: RandomAccessFile): String? {
        var packetNumber = 0
        val packet = ByteArrayOutputStream()
        while (input.filePointer < input.length() && input.filePointer < MAX_TAG_BYTES) {
            if (input.length() - input.filePointer < 27) return null
            val header = ByteArray(27).also(input::readFully)
            if (String(header, 0, 4, Charsets.ISO_8859_1) != "OggS") return null
            val segmentCount = header[26].toInt() and 0xff
            if (input.length() - input.filePointer < segmentCount) return null
            val segments = ByteArray(segmentCount).also(input::readFully)
            for (segment in segments) {
                val length = segment.toInt() and 0xff
                if (length > input.length() - input.filePointer || packet.size() + length > MAX_TAG_BYTES) return null
                packet.write(ByteArray(length).also(input::readFully))
                if (length < 255) {
                    packetNumber++
                    if (packetNumber == 2) {
                        val data = packet.toByteArray()
                        return when {
                            data.size >= 7 && data[0] == 3.toByte() &&
                                String(data, 1, 6, Charsets.ISO_8859_1) == "vorbis" -> readVorbisComments(data, 7)
                            data.size >= 8 && String(data, 0, 8, Charsets.ISO_8859_1) == "OpusTags" ->
                                readVorbisComments(data, 8)
                            else -> null
                        }
                    }
                    packet.reset()
                }
            }
        }
        return null
    }

    private fun readVorbisComments(bytes: ByteArray, start: Int): String? {
        var cursor = start
        fun nextLength(): Int? {
            if (cursor + 4 > bytes.size) return null
            val size = (bytes[cursor].toInt() and 0xff) or
                ((bytes[cursor + 1].toInt() and 0xff) shl 8) or
                ((bytes[cursor + 2].toInt() and 0xff) shl 16) or
                ((bytes[cursor + 3].toInt() and 0xff) shl 24)
            cursor += 4
            return size.takeIf { it >= 0 && it <= bytes.size - cursor }
        }
        val vendorSize = nextLength() ?: return null
        cursor += vendorSize
        val count = nextLength() ?: return null
        if (count > 10_000) return null
        repeat(count) {
            val size = nextLength() ?: return null
            if (size <= MAX_LYRICS_BYTES + 32) {
                val entry = String(bytes, cursor, size, Charsets.UTF_8)
                val separator = entry.indexOf('=')
                if (separator > 0 && entry.substring(0, separator).uppercase() in
                    setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")) {
                    return entry.substring(separator + 1)
                }
            }
            cursor += size
        }
        return null
    }

    private fun readMp4(input: RandomAccessFile, start: Long, end: Long, depth: Int): String? {
        if (depth > 6) return null
        var cursor = start
        while (cursor + 8 <= end) {
            input.seek(cursor)
            var size = input.readInt().toLong() and 0xffffffffL
            val type = ByteArray(4).also(input::readFully).toString(Charsets.ISO_8859_1)
            var headerSize = 8L
            if (size == 1L) {
                if (cursor + 16 > end) return null
                size = input.readLong()
                headerSize = 16L
            } else if (size == 0L) size = end - cursor
            if (size < headerSize || size > end - cursor) return null
            val bodyStart = cursor + headerSize
            val bodyEnd = cursor + size
            when (type) {
                "moov", "udta", "ilst" -> readMp4(input, bodyStart, bodyEnd, depth + 1)?.let { return it }
                "meta" -> if (bodyStart + 4 <= bodyEnd) {
                    readMp4(input, bodyStart + 4, bodyEnd, depth + 1)?.let { return it }
                }
                "©lyr" -> readMp4Lyrics(input, bodyStart, bodyEnd)?.let { return it }
            }
            cursor = bodyEnd
        }
        return null
    }

    private fun readMp4Lyrics(input: RandomAccessFile, start: Long, end: Long): String? {
        if (end - start < 16 || end - start > MAX_TAG_BYTES) return null
        input.seek(start)
        val size = input.readInt().toLong() and 0xffffffffL
        val type = ByteArray(4).also(input::readFully).toString(Charsets.ISO_8859_1)
        if (type != "data" || size < 16 || size > end - start) return null
        val dataType = input.readInt() and 0xffffff
        input.skipBytes(4) // locale
        val bodySize = (size - 16).toInt()
        if (bodySize !in 1..MAX_LYRICS_BYTES * 2) return null
        val body = ByteArray(bodySize).also(input::readFully)
        return String(body, if (dataType == 2) Charsets.UTF_16BE else Charsets.UTF_8)
    }

    private fun readWav(input: RandomAccessFile): String? {
        if (input.length() < 12) return null
        input.seek(0)
        val header = ByteArray(12).also(input::readFully)
        if (String(header, 0, 4, Charsets.ISO_8859_1) != "RIFF" ||
            String(header, 8, 4, Charsets.ISO_8859_1) != "WAVE") return null
        while (input.filePointer + 8 <= input.length()) {
            val type = ByteArray(4).also(input::readFully).toString(Charsets.ISO_8859_1)
            val size = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
            val next = input.filePointer + size + size % 2
            if (next > input.length()) return null
            if (type.equals("ID3 ", ignoreCase = true) && size in 10..MAX_TAG_BYTES.toLong()) {
                val tag = ByteArray(size.toInt()).also(input::readFully)
                if (String(tag, 0, 3, Charsets.ISO_8859_1) == "ID3" &&
                    (tag[3].toInt() and 0xff) in 2..4) {
                    val lyricSize = synchsafe(tag, 6)
                    if (lyricSize in 1..(tag.size - 10)) {
                        parseId3(tag.copyOfRange(0, 10), tag.copyOfRange(10, 10 + lyricSize))?.let { return it }
                    }
                }
            }
            input.seek(next)
        }
        return null
    }

    private fun bigEndian(bytes: ByteArray, start: Int, count: Int): Int {
        var result = 0
        repeat(count) { result = (result shl 8) or (bytes[start + it].toInt() and 0xff) }
        return result
    }

    private fun synchsafe(bytes: ByteArray, start: Int): Int {
        var result = 0
        repeat(4) { result = (result shl 7) or (bytes[start + it].toInt() and 0x7f) }
        return result
    }
}
