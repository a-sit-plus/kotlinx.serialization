/*
 * Copyright 2017-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

/**
 * k.b.cbor.CborBaseline.fromBytes                thrpt   10  1057.030 ±  6.207  ops/ms
 * k.b.cbor.CborBaseline.toBytes                  thrpt   10  1125.402 ±  2.414  ops/ms
 * Benchmark                                       Mode  Cnt     Score    Error   Units
 * k.b.cbor.CborBaseline.fromBytes                thrpt   10  1062.593 ±  4.825  ops/ms
 * k.b.cbor.CborBaseline.toBytes                  thrpt   10  1132.664 ±  3.215  ops/ms
 * Benchmark                Mode  Cnt     Score   Error   Units
 * CborBaseline.fromBytes  thrpt   10  1067.240 ± 7.515  ops/ms
 * CborBaseline.toBytes    thrpt   10  1148.266 ± 8.356  ops/ms
 * Benchmark                Mode  Cnt     Score   Error   Units
 * CborBaseline.fromBytes  thrpt   10  1065.431 ± 4.217  ops/ms
 * CborBaseline.toBytes    thrpt   10  1043.322 ± 5.506  ops/ms
 */

package kotlinx.serialization.cbor.internal

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.cbor.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.*
import kotlin.experimental.*


//value classes are only inlined on the JVM, so we use a typealias and extensions instead
private typealias Stack = MutableList<CborWriter.Data>

private fun Stack(): Stack = mutableListOf()
private fun Stack.push(value: CborWriter.Data) = add(value)
private fun Stack.pop() = removeLast()
private fun Stack.peek() = last()

// Writes class as map [fieldName, fieldValue]
internal open class CborWriter(
    private val cbor: Cbor,
    protected val output: ByteArrayOutput,
) : AbstractEncoder() {
    var isClass = false

    private var encodeByteArrayAsByteString = false

    class Data(val bytes: ByteArrayOutput, var elementCount: Int)


    inner class Preamble(
        private val parentDescriptor: SerialDescriptor?,
        private val index: Int,
        private val label: Long?,
        private val name: String?
    ) {


    }

    /**
     * Encoding requires two passes to support definite length encoding.
     *
     * Tokens are pushed to the stack when a structure starts, and popped when a structure ends. In between the number to children, which **actually** need to be written, are counted
     *
     */
    private val structureStack = Stack()


    override val serializersModule: SerializersModule
        get() = cbor.serializersModule


    @OptIn(ExperimentalSerializationApi::class)
    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {

        if ((encodeByteArrayAsByteString || cbor.alwaysUseByteString)
            && serializer.descriptor == ByteArraySerializer().descriptor
        ) {
            if (structureStack.isEmpty()) output.encodeByteString(value as ByteArray)
            else structureStack.peek().bytes.encodeByteString(value as ByteArray)
        } else {
            encodeByteArrayAsByteString = encodeByteArrayAsByteString || serializer.descriptor.isInlineByteString()
            super.encodeSerializableValue(serializer, value)
        }
    }

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = cbor.encodeDefaults

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        val current = Data(ByteArrayOutput(), 0)
        //    descriptor.getArrayTags()?.forEach { current.bytes.encodeTag(it) }
        structureStack.push(current)
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {

        val completedCurrent = structureStack.pop()
        if (!cbor.writeDefiniteLengths)
            completedCurrent.bytes.end()
        val end = structureStack.isEmpty()


        val accumulator = if (end) output else structureStack.peek().bytes

        //If this nullpointers, we have a structural problem anyhow
        val beginDescriptor = descriptor
        val numChildren = completedCurrent.elementCount

        if (beginDescriptor.hasArrayTag()) {
            beginDescriptor.getArrayTags()?.forEach { accumulator.encodeTag(it) }
            if (cbor.writeDefiniteLengths) accumulator.startArray(numChildren.toULong())
            else accumulator.startArray()
        } else {
            when (beginDescriptor.kind) {
                StructureKind.LIST, is PolymorphicKind -> {
                    if (cbor.writeDefiniteLengths) accumulator.startArray(numChildren.toULong())
                    else accumulator.startArray()
                }

                is StructureKind.MAP -> {
                    if (cbor.writeDefiniteLengths) accumulator.startMap((numChildren / 2).toULong())
                    else accumulator.startMap()
                }

                else -> {
                    if (cbor.writeDefiniteLengths) accumulator.startMap((numChildren).toULong())
                    else accumulator.startMap()
                }
            }
        }
        accumulator.copyFrom(completedCurrent.bytes)
    }


    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        isClass = descriptor.getElementDescriptor(index).kind == StructureKind.CLASS
        encodeByteArrayAsByteString = descriptor.isByteString(index)
        val parent = structureStack.peek()
        val name = descriptor.getElementName(index)
        val label = descriptor.getCborLabel(index)

        if (!descriptor.hasArrayTag()) {
            if (cbor.writeKeyTags) descriptor.getKeyTags(index)?.forEach { parent.bytes.encodeTag(it) }

            if ((descriptor.kind !is StructureKind.LIST) && (descriptor.kind !is StructureKind.MAP) && (descriptor.kind !is PolymorphicKind)) {
                //indices are put into the name field. we don't want to write those, as it would result in double writes
                if (cbor.preferCborLabelsOverNames && label != null) {
                    parent.bytes.encodeNumber(label)
                } else {
                    parent.bytes.encodeString(name)
                }
            }
        }

        if (cbor.writeValueTags) {
            descriptor.getValueTags(index)?.forEach { parent.bytes.encodeTag(it) }
        }
        parent.elementCount++
        return true
    }


    //If any of the following functions are called for serializing raw primitives (i.e. something other than a class,
    // list, map or array, no children exist and the root node needs the data
    override fun encodeString(value: String) {
        if (structureStack.isEmpty()) output.encodeString(value)
        else structureStack.peek().bytes.encodeString(value)
    }


    override fun encodeFloat(value: Float) {
        if (structureStack.isEmpty()) output.encodeFloat(value)
        else structureStack.peek().bytes.encodeFloat(value)
    }


    override fun encodeDouble(value: Double) {
        if (structureStack.isEmpty()) output.encodeDouble(value)
        else structureStack.peek().bytes.encodeDouble(value)
    }


    override fun encodeChar(value: Char) {
        if (structureStack.isEmpty()) output.encodeNumber(value.code.toLong())
        else structureStack.peek().bytes.encodeNumber(value.code.toLong())
    }


    override fun encodeByte(value: Byte) {
        if (structureStack.isEmpty()) output.encodeNumber(value.toLong())
        else structureStack.peek().bytes.encodeNumber(value.toLong())
    }


    override fun encodeShort(value: Short) {
        if (structureStack.isEmpty()) output.encodeNumber(value.toLong())
        else structureStack.peek().bytes.encodeNumber(value.toLong())
    }

    override fun encodeInt(value: Int) {
        if (structureStack.isEmpty()) output.encodeNumber(value.toLong())
        else structureStack.peek().bytes.encodeNumber(value.toLong())
    }


    override fun encodeLong(value: Long) {
        if (structureStack.isEmpty()) output.encodeNumber(value)
        else structureStack.peek().bytes.encodeNumber(value)
    }


    override fun encodeBoolean(value: Boolean) {
        if (structureStack.isEmpty()) output.encodeBoolean(value)
        else structureStack.peek().bytes.encodeBoolean(value)
    }


    override fun encodeNull() {
        val dest = if (structureStack.isEmpty()) output else structureStack.peek().bytes
        if (isClass) dest.encodeEmptyMap()
        else dest.encodeNull()
    }

    @OptIn(ExperimentalSerializationApi::class) // KT-46731
    override fun encodeEnum(
        enumDescriptor: SerialDescriptor,
        index: Int
    ) {
        if (structureStack.isEmpty()) output.encodeString(enumDescriptor.getElementName(index))
        else structureStack.peek().bytes.encodeString(enumDescriptor.getElementName(index))
    }
}


private fun ByteArrayOutput.startArray() = write(BEGIN_ARRAY)

private fun ByteArrayOutput.startArray(size: ULong) {
    val encodedNumber = composePositive(size)
    encodedNumber[0] = encodedNumber[0] or HEADER_ARRAY.toUByte().toByte()
    encodedNumber.forEach { this.writeByte(it.toUByte().toInt()) }
}

private fun ByteArrayOutput.startMap() = write(BEGIN_MAP)

private fun ByteArrayOutput.startMap(size: ULong) {
    val encodedNumber = composePositive(size)
    encodedNumber[0] = encodedNumber[0] or HEADER_MAP.toUByte().toByte()
    encodedNumber.forEach { this.writeByte(it.toUByte().toInt()) }
}

private fun ByteArrayOutput.encodeTag(tag: ULong) {
    val encodedTag = composePositive(tag)
    encodedTag[0] = encodedTag[0] or HEADER_TAG.toUByte().toByte()
    encodedTag.forEach { this.writeByte(it.toUByte().toInt()) }
}

internal fun ByteArrayOutput.end() = write(BREAK)

internal fun ByteArrayOutput.encodeNull() = write(NULL)

internal fun ByteArrayOutput.encodeEmptyMap() = write(EMPTY_MAP)

internal fun ByteArrayOutput.writeByte(byteValue: Int) = write(byteValue)

internal fun ByteArrayOutput.pasteBytes(bytes: ByteArray) {
    write(bytes)
}

internal fun ByteArrayOutput.encodeBoolean(value: Boolean) = write(if (value) TRUE else FALSE)

internal fun ByteArrayOutput.encodeNumber(value: Long) = write(composeNumber(value))

internal fun ByteArrayOutput.encodeByteString(data: ByteArray) {
    this.encodeByteArray(data, HEADER_BYTE_STRING)
}

internal fun ByteArrayOutput.encodeString(value: String) {
    this.encodeByteArray(value.encodeToByteArray(), HEADER_STRING)
}

internal fun ByteArrayOutput.encodeByteArray(data: ByteArray, type: Byte) {
    val header = composeNumber(data.size.toLong())
    header[0] = header[0] or type
    write(header)
    write(data)
}

internal fun ByteArrayOutput.encodeFloat(value: Float) {
    write(NEXT_FLOAT)
    val bits = value.toRawBits()
    for (i in 0..3) {
        write((bits shr (24 - 8 * i)) and 0xFF)
    }
}

internal fun ByteArrayOutput.encodeDouble(value: Double) {
    write(NEXT_DOUBLE)
    val bits = value.toRawBits()
    for (i in 0..7) {
        write(((bits shr (56 - 8 * i)) and 0xFF).toInt())
    }
}

private fun composeNumber(value: Long): ByteArray =
    if (value >= 0) composePositive(value.toULong()) else composeNegative(value)

private fun composePositive(value: ULong): ByteArray = when (value) {
    in 0u..23u -> byteArrayOf(value.toByte())
    in 24u..UByte.MAX_VALUE.toUInt() -> byteArrayOf(24, value.toByte())
    in (UByte.MAX_VALUE.toUInt() + 1u)..UShort.MAX_VALUE.toUInt() -> encodeToByteArray(value, 2, 25)
    in (UShort.MAX_VALUE.toUInt() + 1u)..UInt.MAX_VALUE -> encodeToByteArray(value, 4, 26)
    else -> encodeToByteArray(value, 8, 27)
}

private fun encodeToByteArray(value: ULong, bytes: Int, tag: Byte): ByteArray {
    val result = ByteArray(bytes + 1)
    val limit = bytes * 8 - 8
    result[0] = tag
    for (i in 0 until bytes) {
        result[i + 1] = ((value shr (limit - 8 * i)) and 0xFFu).toByte()
    }
    return result
}

private fun composeNegative(value: Long): ByteArray {
    val aVal = if (value == Long.MIN_VALUE) Long.MAX_VALUE else -1 - value
    val data = composePositive(aVal.toULong())
    data[0] = data[0] or HEADER_NEGATIVE
    return data
}


@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.getArrayTags(): Collection<ULong>? {
    return annotations.filterIsInstance<CborArray>().firstOrNull()?.tag?.map { it.tag }
}