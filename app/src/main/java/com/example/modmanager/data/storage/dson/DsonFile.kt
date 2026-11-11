package com.darkestdungeon.modmanager.data.storage.dson

import com.darkestdungeon.modmanager.data.storage.SaveFormatException
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * The 64-byte DSON container header. All invariants are verified on [parse]
 * against the real save files.
 */
data class DsonHeader(
    val magicNr: Int = DsonTypes.MAGIC_NR,
    /** The 4 raw bytes at 0x04, preserved verbatim (`00 00` + uint16 LE build). */
    val revisionWord: Int = 0,
    val headerLength: Int = DsonTypes.HEADER_SIZE,
    val meta1Size: Int = 0,
    val numMeta1Entries: Int = 0,
    val numMeta2Entries: Int = 0,
    val dataLength: Int = 0,
) {
    val meta1Offset: Int get() = DsonTypes.HEADER_SIZE
    val meta2Offset: Int get() = DsonTypes.HEADER_SIZE + meta1Size
    val dataOffset: Int get() = meta2Offset + numMeta2Entries * DsonTypes.META2_ENTRY_SIZE

    companion object {
        fun parse(bytes: ByteArray): DsonHeader {
            if (bytes.size < DsonTypes.HEADER_SIZE) {
                throw SaveFormatException("The file is too short for a DSON header (${bytes.size} bytes)")
            }
            fun le32(pos: Int) = DsonTypes.readLe32(bytes, pos)
            if (le32(DsonTypes.OFF_MAGIC) != DsonTypes.MAGIC_NR) {
                throw SaveFormatException("Not a DSON save: unexpected magic ${le32(DsonTypes.OFF_MAGIC)}")
            }
            val headerLength = le32(DsonTypes.OFF_HEADER_LENGTH)
            if (headerLength != DsonTypes.HEADER_SIZE) {
                throw SaveFormatException("Unexpected DSON header length $headerLength")
            }
            val numMeta1 = le32(DsonTypes.OFF_NUM_META1)
            val meta1Size = le32(DsonTypes.OFF_META1_SIZE)
            if (meta1Size != numMeta1 shl DsonTypes.META1_SIZE_SHIFT) {
                throw SaveFormatException("meta1Size $meta1Size does not match numMeta1 $numMeta1")
            }
            if (numMeta1 <= 0) {
                throw SaveFormatException("The save declares no objects (numMeta1=$numMeta1)")
            }
            val numMeta2 = le32(DsonTypes.OFF_NUM_META2)
            if (numMeta2 <= 0) {
                throw SaveFormatException("The save declares no fields (numMeta2=$numMeta2)")
            }
            val dataLength = le32(DsonTypes.OFF_DATA_LENGTH)
            val header = DsonHeader(
                magicNr = le32(DsonTypes.OFF_MAGIC),
                revisionWord = le32(DsonTypes.OFF_REVISION),
                headerLength = headerLength,
                meta1Size = meta1Size,
                numMeta1Entries = numMeta1,
                numMeta2Entries = numMeta2,
                dataLength = dataLength,
            )
            if (le32(DsonTypes.OFF_ZEROES_1) != 0) {
                throw SaveFormatException("Header zero field at 0x0C is not zero")
            }
            if (le32(DsonTypes.OFF_META1_OFFSET) != header.meta1Offset) {
                throw SaveFormatException(
                    "meta1Offset ${le32(DsonTypes.OFF_META1_OFFSET)} != ${header.meta1Offset}"
                )
            }
            if (le32(DsonTypes.OFF_META2_OFFSET) != header.meta2Offset) {
                throw SaveFormatException(
                    "meta2Offset ${le32(DsonTypes.OFF_META2_OFFSET)} != ${header.meta2Offset}"
                )
            }
            if (le32(DsonTypes.OFF_ZEROES_4) != 0) {
                throw SaveFormatException("Header zero field at 0x34 is not zero")
            }
            if (header.dataOffset % 4 != 0) {
                throw SaveFormatException("dataOffset ${header.dataOffset} is not 4-aligned")
            }
            if (header.dataOffset + dataLength != bytes.size) {
                throw SaveFormatException(
                    "dataOffset + dataLength (${header.dataOffset + dataLength}) != file size (${bytes.size})"
                )
            }
            return header
        }
    }

    fun encode(): ByteArray {
        val out = ByteArray(DsonTypes.HEADER_SIZE)
        DsonTypes.writeLe32(out, DsonTypes.OFF_MAGIC, magicNr)
        DsonTypes.writeLe32(out, DsonTypes.OFF_REVISION, revisionWord)
        DsonTypes.writeLe32(out, DsonTypes.OFF_HEADER_LENGTH, headerLength)
        DsonTypes.writeLe32(out, DsonTypes.OFF_META1_SIZE, meta1Size)
        DsonTypes.writeLe32(out, DsonTypes.OFF_NUM_META1, numMeta1Entries)
        DsonTypes.writeLe32(out, DsonTypes.OFF_META1_OFFSET, meta1Offset)
        DsonTypes.writeLe32(out, DsonTypes.OFF_NUM_META2, numMeta2Entries)
        DsonTypes.writeLe32(out, DsonTypes.OFF_META2_OFFSET, meta2Offset)
        DsonTypes.writeLe32(out, DsonTypes.OFF_ZEROES_4, 0)
        DsonTypes.writeLe32(out, DsonTypes.OFF_DATA_LENGTH, dataLength)
        DsonTypes.writeLe32(out, DsonTypes.OFF_DATA_OFFSET, dataOffset)
        return out
    }
}

/** One 16-byte Meta1 (object directory) entry. */
data class Meta1Entry(
    val parentIndex: Int,
    val meta2EntryIdx: Int,
    val numDirectChildren: Int,
    val numAllChildren: Int,
)

/** One 12-byte Meta2 (field directory) entry. */
data class Meta2Entry(
    val nameHash: Int,
    /** RELATIVE offset of the field's name bytes from the data block start. */
    val offset: Int,
    val fieldInfo: Int,
) {
    val isObject: Boolean get() = DsonTypes.isObjectField(fieldInfo)

    /** Name length INCLUDING the trailing `\0`. */
    val nameLen: Int get() = DsonTypes.nameLengthOf(fieldInfo)

    /** Meta1 row index (meaningful for object fields only). */
    val meta1Idx: Int get() = DsonTypes.meta1IndexOf(fieldInfo)
}

/** The primitive value kinds the decoder can infer from a field's span. */
enum class DsonPrimitiveKind {
    /** 1 unaligned byte. */
    BOOL_OR_CHAR,
    /** 4 aligned bytes. */
    INT_OR_FLOAT,
    /** 8 aligned bytes. */
    TWO_INT_OR_TWO_BOOL,
    /** Aligned `len32 + payload`; the length INCLUDES the trailing `\0`. */
    STRING,
    /** Same layout as [STRING]; the payload starts with the DSON magic. */
    FILE,
    /** Aligned `count32 + count × int32`. */
    INT_VECTOR,
    /** Aligned `count32 + count × (len32 + payload incl. \0)`. */
    STRING_VECTOR,
    /** Aligned opaque blob (FloatArray fallback; not expected in mod regions). */
    RAW_ALIGNED,
}

/** A tree field: either an object or a primitive. */
sealed interface DsonField {
    val name: String

    /** Data-block-relative offset of this field's name bytes; -1 when synthesized. */
    val fieldOffset: Int

    /** Byte span of this whole field in the data block; -1 when synthesized. */
    val fieldSpan: Int
}

/**
 * An object field. [objectName] would be the object's own value bytes inside
 * the data block — EMPIRICALLY ABSENT on real saves (every object field spans
 * exactly its name, e.g. `applied_ugcs_1_0\0` = 17 bytes), so it stays null
 * and nothing is emitted for it.
 */
class DsonObject(
    override val name: String,
    val objectName: String? = null,
    /** Original fieldInfo flags, preserved when a decoded object is re-encoded. */
    val rawFieldInfo: Int? = null,
    val children: MutableList<DsonField> = mutableListOf(),
    override val fieldOffset: Int = -1,
    override val fieldSpan: Int = -1,
) : DsonField {
    override fun toString(): String = "DsonObject('$name', children=${children.size})"
}

/**
 * A primitive field.
 *
 * [valueBytes] is the field's VALUE as positioned in the data block:
 *  - STRING/FILE: the payload AFTER the len32 (length includes the `\0`);
 *  - INT_VECTOR/STRING_VECTOR: count + elements;
 *  - scalars: the raw little-endian bytes.
 *
 * It is kept verbatim so untouched fields re-emit byte-exactly, while the
 * value's LEADING alignment pad is always RECOMPUTED by the emitter (the pad
 * depends on the field's position, which shifts when earlier fields change).
 *
 * [rawFieldInfo] preserves the source fieldInfo word bit-for-bit (real saves
 * carry a stray high bit on a couple of primitives); null for synthesized
 * fields, in which case a clean word is computed from the name length.
 */
class DsonPrimitive(
    override val name: String,
    val kind: DsonPrimitiveKind,
    val valueBytes: ByteArray,
    val rawFieldInfo: Int? = null,
    override val fieldOffset: Int = -1,
    override val fieldSpan: Int = -1,
    /** Decoded string value (STRING/FILE): the payload with one trailing `\0` trimmed. */
    val stringValue: String? = null,
    val intElements: List<Int>? = null,
    val stringElements: List<String>? = null,
) : DsonField {
    override fun toString(): String = "DsonPrimitive('$name', $kind, ${valueBytes.size}B)"
}

/** A fully decoded DSON save: header + object tree. */
class DsonFile(
    val header: DsonHeader,
    val root: DsonObject,
)

/**
 * The DSON codec for the Wicked Android port's `persist.game.json`.
 *
 * Byte-verified layout (against two real saves):
 *  - Fields are laid out contiguously in canonical depth-first order; a
 *    field's size is `nextMeta2.offset - offset` (the last field ends at the
 *    data block end).
 *  - Object fields carry NO value bytes: their span is exactly their name
 *    (`nameLen` includes the `\0`). The next field follows immediately.
 *  - Primitive String/File/vector values are 4-ALIGNED within the data block
 *    (0-3 zero pad bytes after the name); Bool/Char are a single UNALIGNED
 *    byte right after the name; Int/Float/TwoInt/TwoBool are aligned scalars.
 *  - Meta2.nameHash == stringHash(fieldName) for EVERY field of both files.
 *  - Meta1 rows are ordered depth-first; row 0 is the root (parentIndex -1).
 */
object DsonCodec {

    // --------------------------------------------------------------- decode

    fun decode(bytes: ByteArray): DsonFile {
        val header = DsonHeader.parse(bytes)
        val dataOffset = header.dataOffset
        val dataEnd = dataOffset + header.dataLength
        val numMeta1 = header.numMeta1Entries
        val numMeta2 = header.numMeta2Entries

        var p = header.meta1Offset
        val meta1 = ArrayList<Meta1Entry>(numMeta1)
        repeat(numMeta1) {
            meta1 += Meta1Entry(
                parentIndex = DsonTypes.readLe32(bytes, p),
                meta2EntryIdx = DsonTypes.readLe32(bytes, p + 4),
                numDirectChildren = DsonTypes.readLe32(bytes, p + 8),
                numAllChildren = DsonTypes.readLe32(bytes, p + 12),
            )
            p += DsonTypes.META1_ENTRY_SIZE
        }

        p = header.meta2Offset
        val meta2 = ArrayList<Meta2Entry>(numMeta2)
        repeat(numMeta2) {
            meta2 += Meta2Entry(
                nameHash = DsonTypes.readLe32(bytes, p),
                offset = DsonTypes.readLe32(bytes, p + 4),
                fieldInfo = DsonTypes.readLe32(bytes, p + 8),
            )
            p += DsonTypes.META2_ENTRY_SIZE
        }

        val rootMeta = meta1[0]
        if (rootMeta.parentIndex != -1) {
            throw SaveFormatException("meta1[0].parentIndex is ${rootMeta.parentIndex}, expected -1 (root)")
        }
        if (rootMeta.meta2EntryIdx != 0) {
            throw SaveFormatException(
                "The root field must be Meta2[0] but is Meta2[${rootMeta.meta2EntryIdx}]"
            )
        }
        val rootEntry = meta2[0]
        if (rootEntry.offset != 0) {
            throw SaveFormatException("The root field must start at data offset 0 but is at ${rootEntry.offset}")
        }
        if (!rootEntry.isObject) {
            throw SaveFormatException("The root field is not an object")
        }

        // Stack discipline: each frame = (object, remaining direct children).
        val root = DsonObject(
            name = readName(bytes, dataOffset, dataEnd, rootEntry),
            rawFieldInfo = rootEntry.fieldInfo,
            fieldOffset = rootEntry.offset,
            fieldSpan = rootEntry.nameLen,
        )
        val stack = ArrayDeque<Pair<DsonObject, Int>>()
        stack.addLast(root to rootMeta.numDirectChildren)

        for (i in 1 until numMeta2) {
            val entry = meta2[i]
            if (stack.isEmpty()) {
                throw SaveFormatException(
                    "Meta2[$i] ('${describeName(bytes, dataOffset, entry)}') has no parent object to hold it"
                )
            }
            val (parent, remaining) = stack.removeLast()
            if (remaining <= 0) {
                throw SaveFormatException(
                    "Meta2[$i] ('${describeName(bytes, dataOffset, entry)}') overflows its parent's child count"
                )
            }
            val nextRel = if (i + 1 < numMeta2) meta2[i + 1].offset else header.dataLength
            if (entry.isObject) {
                val meta1Idx = entry.meta1Idx
                if (meta1Idx !in 0 until numMeta1) {
                    throw SaveFormatException(
                        "Meta2[$i] ('${describeName(bytes, dataOffset, entry)}') points at Meta1[$meta1Idx], " +
                            "out of bounds"
                    )
                }
                val objMeta = meta1[meta1Idx]
                if (objMeta.parentIndex < 0) {
                    throw SaveFormatException("Meta1[$meta1Idx] claims to be a second root")
                }
                val child = DsonObject(
                    name = readName(bytes, dataOffset, dataEnd, entry),
                    rawFieldInfo = entry.fieldInfo,
                    fieldOffset = entry.offset,
                    fieldSpan = entry.nameLen,
                )
                parent.children += child
                stack.addLast(parent to remaining - 1)
                stack.addLast(child to objMeta.numDirectChildren)
            } else {
                parent.children += decodePrimitive(bytes, dataOffset, dataEnd, entry, nextRel)
                stack.addLast(parent to remaining - 1)
            }
            while (stack.isNotEmpty() && stack.last().second == 0) stack.removeLast()
        }
        if (stack.isNotEmpty()) {
            throw SaveFormatException(
                "The object tree ends with ${stack.sumOf { it.second }} unread field(s) " +
                    "(first under '${stack.last().first.name}')"
            )
        }
        return DsonFile(header, root)
    }

    private fun describeName(bytes: ByteArray, dataOffset: Int, entry: Meta2Entry): String =
        runCatching { readName(bytes, dataOffset, dataOffset + Int.MAX_VALUE, entry) }.getOrElse { "?" }

    private fun readName(bytes: ByteArray, dataOffset: Int, dataEnd: Int, entry: Meta2Entry): String {
        val nameLen = entry.nameLen
        if (nameLen <= 0) {
            throw SaveFormatException("Field at data offset ${entry.offset} has a zero name length")
        }
        val start = dataOffset + entry.offset
        if (start < 0 || start + nameLen > dataEnd) {
            throw SaveFormatException(
                "Field name at data offset ${entry.offset} (length $nameLen) exceeds the data block"
            )
        }
        val raw = bytes.copyOfRange(start, start + nameLen)
        if (raw[nameLen - 1] != 0.toByte()) {
            throw SaveFormatException("Field name at data offset ${entry.offset} is not null-terminated")
        }
        return raw.copyOfRange(0, nameLen - 1).toString(StandardCharsets.UTF_8)
    }

    /**
     * Infers and decodes one primitive field from its span
     * (`nextRel - entry.offset`), validating the structure it claims.
     */
    private fun decodePrimitive(
        bytes: ByteArray,
        dataOffset: Int,
        dataEnd: Int,
        entry: Meta2Entry,
        nextRel: Int,
    ): DsonPrimitive {
        val name = readName(bytes, dataOffset, dataEnd, entry)
        val relOff = entry.offset
        val span = nextRel - relOff
        val nameEnd = relOff + entry.nameLen
        if (nextRel < nameEnd) {
            throw SaveFormatException("Field '$name' at data offset $relOff has a negative span")
        }
        val rest = span - entry.nameLen
        val absNameEnd = dataOffset + nameEnd
        fun abs(rel: Int) = dataOffset + rel

        // 1. Single unaligned byte: Bool / Char.
        if (rest == 1) {
            val raw = bytes.copyOfRange(absNameEnd, absNameEnd + 1)
            return DsonPrimitive(
                name, DsonPrimitiveKind.BOOL_OR_CHAR, raw,
                rawFieldInfo = entry.fieldInfo, fieldOffset = relOff, fieldSpan = span,
            )
        }
        if (rest < 4) {
            throw SaveFormatException(
                "Field '$name' (data offset $relOff, span $span) has a $rest-byte value; cannot infer its type"
            )
        }

        // 2. Aligned kinds: pad after the name, then the value.
        val pad = DsonTypes.padToAlign(nameEnd)
        for (k in 0 until pad) {
            if (bytes[absNameEnd + k] != 0.toByte()) {
                throw SaveFormatException(
                    "Field '$name' (data offset $relOff) has a non-zero alignment pad byte"
                )
            }
        }
        val valueStart = nameEnd + pad
        val valueLen = rest - pad
        val valueBytes = bytes.copyOfRange(abs(valueStart), abs(valueStart) + valueLen)

        // 3. String / File: len32 + payload with len32 == payload size.
        if (valueLen >= 5) {
            val len = DsonTypes.readLe32(valueBytes, 0)
            if (len == valueLen - 4) {
                val payload = valueBytes.copyOfRange(4, valueLen)
                val isFile = payload.size >= 4 && DsonTypes.readLe32(payload, 0) == DsonTypes.MAGIC_NR
                val decoded = payload.toString(StandardCharsets.UTF_8).trimEnd { it == '\u0000' }
                return DsonPrimitive(
                    name,
                    if (isFile) DsonPrimitiveKind.FILE else DsonPrimitiveKind.STRING,
                    payload,
                    rawFieldInfo = entry.fieldInfo,
                    fieldOffset = relOff,
                    fieldSpan = span,
                    stringValue = decoded,
                )
            }
        }

        // 4. IntVector: count32 + count × int32.
        if (valueLen >= 4) {
            val count = DsonTypes.readLe32(valueBytes, 0)
            if (count >= 0 && valueLen == 4 + 4 * count) {
                val ints = (0 until count).map { DsonTypes.readLe32(valueBytes, 4 + 4 * it) }
                return DsonPrimitive(
                    name, DsonPrimitiveKind.INT_VECTOR, valueBytes,
                    rawFieldInfo = entry.fieldInfo, fieldOffset = relOff, fieldSpan = span,
                    intElements = ints,
                )
            }
        }

        // 5. StringVector: count32 + count × (len32 + payload).
        if (valueLen >= 4) {
            val count = DsonTypes.readLe32(valueBytes, 0)
            var pos = 4
            var ok = count >= 0
            val strings = ArrayList<String>(count.coerceAtMost(1024))
            repeat(count) {
                if (!ok || pos + 4 > valueLen) {
                    ok = false
                    return@repeat
                }
                val len = DsonTypes.readLe32(valueBytes, pos)
                if (len < 1 || pos + 4 + len > valueLen) {
                    ok = false
                    return@repeat
                }
                strings += valueBytes.copyOfRange(pos + 4, pos + 4 + len)
                    .toString(StandardCharsets.UTF_8).trimEnd { c -> c == '\u0000' }
                pos += 4 + len
            }
            if (ok && pos == valueLen) {
                return DsonPrimitive(
                    name, DsonPrimitiveKind.STRING_VECTOR, valueBytes,
                    rawFieldInfo = entry.fieldInfo, fieldOffset = relOff, fieldSpan = span,
                    stringElements = strings,
                )
            }
        }

        // 6. Rare fallback: an aligned opaque blob (e.g. FloatArray).
        if (valueLen % 4 == 0) {
            return DsonPrimitive(
                name, DsonPrimitiveKind.RAW_ALIGNED, valueBytes,
                rawFieldInfo = entry.fieldInfo, fieldOffset = relOff, fieldSpan = span,
            )
        }
        throw SaveFormatException(
            "Field '$name' (data offset $relOff, name length ${entry.nameLen}, span $span) has a value " +
                "that matches no known DSON type"
        )
    }

    // --------------------------------------------------------------- encode

    fun encode(file: DsonFile): ByteArray {
        val data = ByteArrayOutputStream()
        val meta2 = ArrayList<Meta2Entry>()
        val meta1 = ArrayList<Meta1Entry>()

        /** Emits one primitive field (name + pad + value). */
        fun emitPrimitive(p: DsonPrimitive) {
            val start = data.size()
            val nameBytes = nameBytes(p.name)
            data.write(nameBytes)
            when (p.kind) {
                DsonPrimitiveKind.BOOL_OR_CHAR -> {
                    require(p.valueBytes.size == 1) { "Bool/Char field '${p.name}' must hold 1 byte" }
                    data.write(p.valueBytes)
                }
                DsonPrimitiveKind.INT_OR_FLOAT,
                DsonPrimitiveKind.TWO_INT_OR_TWO_BOOL,
                DsonPrimitiveKind.INT_VECTOR,
                DsonPrimitiveKind.STRING_VECTOR,
                DsonPrimitiveKind.RAW_ALIGNED,
                -> {
                    writePad(data)
                    data.write(p.valueBytes)
                }
                DsonPrimitiveKind.STRING,
                DsonPrimitiveKind.FILE,
                -> {
                    writePad(data)
                    data.write(le32(p.valueBytes.size))
                    data.write(p.valueBytes)
                }
            }
            val fieldInfo = p.rawFieldInfo
                ?: DsonTypes.fieldInfo(isObject = false, nameLen = nameBytes.size)
            meta2 += Meta2Entry(
                nameHash = DsonTypes.stringHash(p.name),
                offset = start,
                fieldInfo = fieldInfo,
            )
        }

        /** Emits one object field (name + subtree); returns its Meta1 row index. */
        fun emitObject(obj: DsonObject, parentMeta1Idx: Int): Int {
            val start = data.size()
            val nameBytes = nameBytes(obj.name)
            data.write(nameBytes)
            if (obj.objectName != null) {
                // Real saves carry no object value bytes; if a caller set them,
                // preserve them verbatim.
                data.write(nameBytes(obj.objectName))
            }
            val meta1Idx = meta1.size
            meta1 += Meta1Entry(
                parentIndex = parentMeta1Idx,
                meta2EntryIdx = meta2.size,
                numDirectChildren = obj.children.size,
                numAllChildren = countAllFields(obj),
            )
            // Preserve object flags from the original save (including the
            // high-bit object flag used by the device save), but replace the
            // embedded Meta1 index with the newly emitted row index.
            val fieldInfo = obj.rawFieldInfo?.let { raw ->
                (raw and 0x800007FF.toInt()) or (meta1Idx shl DsonTypes.META1_INDEX_SHIFT)
            } ?: DsonTypes.fieldInfo(
                isObject = true,
                nameLen = nameBytes.size,
                meta1Idx = meta1Idx
            )
            meta2 += Meta2Entry(
                nameHash = DsonTypes.stringHash(obj.name),
                offset = start,
                fieldInfo = fieldInfo,
            )
            for (child in obj.children) {
                if (child is DsonObject) {
                    emitObject(child, meta1Idx)
                } else {
                    emitPrimitive(child as DsonPrimitive)
                }
            }
            return meta1Idx
        }

        // The root field is Meta2[0] with Meta1 row 0 and parent -1.
        val root = file.root
        val rootNameBytes = nameBytes(root.name)
        if (root.objectName != null && root.objectName != root.name) {
            throw SaveFormatException("The root object's value bytes must repeat its name; refusing to encode")
        }
        val rootStart = data.size()
        data.write(rootNameBytes)
        meta1 += Meta1Entry(
            parentIndex = -1,
            meta2EntryIdx = 0,
            numDirectChildren = root.children.size,
            numAllChildren = countAllFields(root),
        )
        meta2 += Meta2Entry(
            nameHash = DsonTypes.stringHash(root.name),
            offset = rootStart,
            fieldInfo = root.rawFieldInfo?.let { raw ->
                raw and 0x800007FF.toInt()
            } ?: DsonTypes.fieldInfo(isObject = true, nameLen = rootNameBytes.size, meta1Idx = 0),
        )
        for (child in root.children) {
            if (child is DsonObject) {
                emitObject(child, 0)
            } else {
                emitPrimitive(child as DsonPrimitive)
            }
        }

        val dataBytes = data.toByteArray()
        val numMeta1 = meta1.size
        val numMeta2 = meta2.size
        val header = DsonHeader(
            magicNr = file.header.magicNr,
            revisionWord = file.header.revisionWord,
            headerLength = DsonTypes.HEADER_SIZE,
            meta1Size = numMeta1 shl DsonTypes.META1_SIZE_SHIFT,
            numMeta1Entries = numMeta1,
            numMeta2Entries = numMeta2,
            dataLength = dataBytes.size,
        )
        // All header invariants must hold for the game's loader.
        if (header.meta1Offset != DsonTypes.HEADER_SIZE) {
            throw SaveFormatException("Rebuilt meta1Offset ${header.meta1Offset} is invalid")
        }
        if (header.dataOffset % 4 != 0) {
            throw SaveFormatException("Rebuilt dataOffset ${header.dataOffset} is not 4-aligned")
        }

        val out = ByteArrayOutputStream(header.dataOffset + dataBytes.size)
        out.write(header.encode())
        for (e in meta1) {
            val b = ByteArray(DsonTypes.META1_ENTRY_SIZE)
            DsonTypes.writeLe32(b, 0, e.parentIndex)
            DsonTypes.writeLe32(b, 4, e.meta2EntryIdx)
            DsonTypes.writeLe32(b, 8, e.numDirectChildren)
            DsonTypes.writeLe32(b, 12, e.numAllChildren)
            out.write(b)
        }
        for (e in meta2) {
            val b = ByteArray(DsonTypes.META2_ENTRY_SIZE)
            DsonTypes.writeLe32(b, 0, e.nameHash)
            DsonTypes.writeLe32(b, 4, e.offset)
            DsonTypes.writeLe32(b, 8, e.fieldInfo)
            out.write(b)
        }
        out.write(dataBytes)
        return out.toByteArray()
    }

    /** Number of descendant FIELDS (objects + primitives) under [obj]. */
    private fun countAllFields(obj: DsonObject): Int {
        var n = 0
        for (c in obj.children) {
            n++
            if (c is DsonObject) n += countAllFields(c)
        }
        return n
    }

    /** 0-3 zero bytes to 4-align the data-block position. */
    private fun writePad(out: ByteArrayOutputStream) {
        var pad = (4 - (out.size() and 0x3)) and 0x3
        while (pad-- > 0) out.write(0)
    }

    /** A DSON field/object name as stored: UTF-8 + trailing `\0`. */
    private fun nameBytes(name: String): ByteArray =
        name.toByteArray(StandardCharsets.UTF_8) + 0

    private fun le32(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )
}