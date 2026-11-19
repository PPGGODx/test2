package com.hybridmusic.player.util

import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object M4aTagWriter {

    private data class Mp4Box(
        val type: String,
        val headerSize: Int,
        val totalSize: Long,
        val payloadOffset: Long,
        val payloadSize: Long
    )

    /**
     * Embeds Title (©nam), Artist (©ART), Album (©alb), and Cover Art (covr)
     * directly inside an MP4/M4A file (moov -> udta -> meta -> ilst).
     */
    fun embedMetadata(
        inputFile: File,
        outputFile: File,
        title: String,
        artist: String,
        album: String = "Zong",
        coverBytes: ByteArray?
    ): Boolean {
        return runCatching {
            if (!inputFile.exists() || inputFile.length() < 16L) return false

            val topBoxes = parseTopLevelBoxes(inputFile)
            if (topBoxes.none { it.type == "ftyp" }) {
                inputFile.copyTo(outputFile, overwrite = true)
                return false
            }

            val moovIndex = topBoxes.indexOfFirst { it.type == "moov" }
            val mdatIndex = topBoxes.indexOfFirst { it.type == "mdat" }
            if (moovIndex == -1 || mdatIndex == -1) {
                inputFile.copyTo(outputFile, overwrite = true)
                return false
            }

            val moovBox = topBoxes[moovIndex]
            val oldMoovPayload = readFileSlice(inputFile, moovBox.payloadOffset, moovBox.payloadSize.toInt())
            val moovChildrenWithoutUdta = stripChildBox(oldMoovPayload, "udta")

            val newUdtaBox = buildUdtaBox(
                title = title,
                artist = artist,
                album = album,
                coverBytes = coverBytes
            )

            val rawNewMoovPayload = moovChildrenWithoutUdta + newUdtaBox
            val newMoovTotalSize = 8L + rawNewMoovPayload.size.toLong()
            val delta = newMoovTotalSize - moovBox.totalSize

            // Shift stco/co64 chunk offsets if moov precedes mdat
            val adjustedMoovPayload = if (moovIndex < mdatIndex && delta != 0L) {
                adjustChunkOffsetsInMoov(rawNewMoovPayload, delta)
            } else {
                rawNewMoovPayload
            }

            val newMoovBoxBytes = makeBox("moov", adjustedMoovPayload)

            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().buffered().use { out ->
                RandomAccessFile(inputFile, "r").use { raf ->
                    for ((idx, box) in topBoxes.withIndex()) {
                        if (idx == moovIndex) {
                            out.write(newMoovBoxBytes)
                        } else {
                            copyRange(raf, box.payloadOffset - box.headerSize, box.totalSize, out)
                        }
                    }
                }
                out.flush()
            }

            // Verify resulting audio file integrity via MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            val valid = try {
                retriever.setDataSource(outputFile.absolutePath)
                val checkTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                !checkTitle.isNullOrBlank()
            } catch (_: Exception) {
                false
            } finally {
                runCatching { retriever.release() }
            }

            if (!valid) {
                inputFile.copyTo(outputFile, overwrite = true)
            }
            valid
        }.getOrElse {
            runCatching { inputFile.copyTo(outputFile, overwrite = true) }
            false
        }
    }

    private fun parseTopLevelBoxes(file: File): List<Mp4Box> {
        val list = mutableListOf<Mp4Box>()
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            var pos = 0L
            val header = ByteArray(8)
            val extHeader = ByteArray(8)
            while (pos + 8L <= len) {
                raf.seek(pos)
                raf.readFully(header)
                val buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
                val size32 = buf.int.toLong() and 0xFFFFFFFFL
                val type = String(header, 4, 4, Charsets.ISO_8859_1)

                val headerSize: Int
                val totalSize: Long
                when (size32) {
                    0L -> {
                        headerSize = 8
                        totalSize = len - pos
                    }
                    1L -> {
                        if (pos + 16L > len) break
                        raf.readFully(extHeader)
                        totalSize = ByteBuffer.wrap(extHeader).order(ByteOrder.BIG_ENDIAN).long
                        headerSize = 16
                    }
                    else -> {
                        headerSize = 8
                        totalSize = size32
                    }
                }
                if (totalSize < headerSize || pos + totalSize > len) break
                list.add(
                    Mp4Box(
                        type = type,
                        headerSize = headerSize,
                        totalSize = totalSize,
                        payloadOffset = pos + headerSize,
                        payloadSize = totalSize - headerSize
                    )
                )
                pos += totalSize
            }
        }
        return list
    }

    private fun buildUdtaBox(
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?
    ): ByteArray {
        val hdlrPayload = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 0)) // predefined
            write("mdir".toByteArray(Charsets.ISO_8859_1))
            write("appl".toByteArray(Charsets.ISO_8859_1))
            write(byteArrayOf(0, 0, 0, 0)) // flags
            write(byteArrayOf(0, 0, 0, 0)) // flags mask
            write(0) // empty name
        }.toByteArray()
        val hdlrBox = makeFullBox("hdlr", 0, 0, hdlrPayload)

        val ilstStream = ByteArrayOutputStream()
        if (title.isNotBlank()) {
            ilstStream.write(makeIlstTextAtom("\u00A9nam", title))
        }
        if (artist.isNotBlank()) {
            ilstStream.write(makeIlstTextAtom("\u00A9ART", artist))
        }
        if (album.isNotBlank()) {
            ilstStream.write(makeIlstTextAtom("\u00A9alb", album))
        }
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            val isPng = coverBytes.size >= 8 &&
                coverBytes[0] == 0x89.toByte() &&
                coverBytes[1] == 0x50.toByte() &&
                coverBytes[2] == 0x4E.toByte() &&
                coverBytes[3] == 0x47.toByte()
            val dataType = if (isPng) 14 else 13 // 13 = JPEG, 14 = PNG
            val dataPayload = byteArrayOf(0, 0, 0, 0) + coverBytes
            val dataBox = makeFullBox("data", 0, dataType, dataPayload)
            ilstStream.write(makeBox("covr", dataBox))
        }

        val ilstBox = makeBox("ilst", ilstStream.toByteArray())
        val metaBox = makeFullBox("meta", 0, 0, hdlrBox + ilstBox)
        return makeBox("udta", metaBox)
    }

    private fun makeIlstTextAtom(atomType: String, value: String): ByteArray {
        val textBytes = value.toByteArray(Charsets.UTF_8)
        val dataPayload = byteArrayOf(0, 0, 0, 0) + textBytes
        val dataBox = makeFullBox("data", 0, 1, dataPayload) // type 1 = UTF-8
        return makeBox(atomType, dataBox)
    }

    private fun makeBox(type: String, payload: ByteArray): ByteArray {
        val total = 8 + payload.size
        val out = ByteArray(total)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(total)
        buf.put(type.toByteArray(Charsets.ISO_8859_1), 0, 4)
        System.arraycopy(payload, 0, out, 8, payload.size)
        return out
    }

    private fun makeFullBox(type: String, version: Int, flags: Int, payload: ByteArray): ByteArray {
        val vf = ((version and 0xFF) shl 24) or (flags and 0xFFFFFF)
        val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(vf).array()
        return makeBox(type, header + payload)
    }

    private fun stripChildBox(containerPayload: ByteArray, targetType: String): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = 0
        while (pos + 8 <= containerPayload.size) {
            val size = ByteBuffer.wrap(containerPayload, pos, 4).order(ByteOrder.BIG_ENDIAN).int
            val type = String(containerPayload, pos + 4, 4, Charsets.ISO_8859_1)
            if (size < 8 || pos + size > containerPayload.size) {
                out.write(containerPayload, pos, containerPayload.size - pos)
                break
            }
            if (type != targetType) {
                out.write(containerPayload, pos, size)
            }
            pos += size
        }
        return out.toByteArray()
    }

    private fun adjustChunkOffsetsInMoov(moovPayload: ByteArray, delta: Long): ByteArray {
        val copy = moovPayload.copyOf()
        val containerTypes = setOf("trak", "mdia", "minf", "stbl")

        fun walk(start: Int, end: Int) {
            var pos = start
            while (pos + 8 <= end) {
                val size = ByteBuffer.wrap(copy, pos, 4).order(ByteOrder.BIG_ENDIAN).int
                val type = String(copy, pos + 4, 4, Charsets.ISO_8859_1)
                if (size < 8 || pos + size > end) break

                if (type in containerTypes) {
                    walk(pos + 8, pos + size)
                } else if (type == "stco" && pos + 16 <= pos + size) {
                    val buf = ByteBuffer.wrap(copy, pos + 8, size - 8).order(ByteOrder.BIG_ENDIAN)
                    buf.int // version + flags
                    val entryCount = buf.int
                    for (i in 0 until entryCount) {
                        if (buf.remaining() < 4) break
                        val curOffset = buf.getInt(buf.position()).toLong() and 0xFFFFFFFFL
                        buf.putInt((curOffset + delta).toInt())
                    }
                } else if (type == "co64" && pos + 16 <= pos + size) {
                    val buf = ByteBuffer.wrap(copy, pos + 8, size - 8).order(ByteOrder.BIG_ENDIAN)
                    buf.int // version + flags
                    val entryCount = buf.int
                    for (i in 0 until entryCount) {
                        if (buf.remaining() < 8) break
                        val curOffset = buf.getLong(buf.position())
                        buf.putLong(curOffset + delta)
                    }
                }
                pos += size
            }
        }

        walk(0, copy.size)
        return copy
    }

    private fun readFileSlice(file: File, offset: Long, length: Int): ByteArray {
        val data = ByteArray(length)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            raf.readFully(data)
        }
        return data
    }

    private fun copyRange(raf: RandomAccessFile, offset: Long, length: Long, out: OutputStream) {
        raf.seek(offset)
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0L) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = raf.read(buffer, 0, toRead)
            if (read <= 0) break
            out.write(buffer, 0, read)
            remaining -= read
        }
    }
}
