/*
 * Copyright 2017-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.serialization.cbor

import kotlinx.serialization.*
import kotlin.test.*


class CbrWriterTest {
    @Test
    fun writeSimpleClass() {
        assertEquals("a1616163737472", Cbor.encodeToHexString(Simple.serializer(), Simple("str")))
    }

    @Test
    fun writeComplicatedClass() {
        val test = TypesUmbrella(
            "Hello, world!",
            42,
            null,
            listOf("a", "b"),
            mapOf(1 to true, 2 to false),
            Simple("lol"),
            listOf(Simple("kek")),
            HexConverter.parseHexBinary("cafe"),
            HexConverter.parseHexBinary("cafe")
        )
        /**
         * A9                               # map(9)
         *    63                            # text(3)
         *       737472                     # "str"
         *    6D                            # text(13)
         *       48656C6C6F2C20776F726C6421 # "Hello, world!"
         *    61                            # text(1)
         *       69                         # "i"
         *    18 2A                         # unsigned(42)
         *    68                            # text(8)
         *       6E756C6C61626C65           # "nullable"
         *    F6                            # primitive(22)
         *    64                            # text(4)
         *       6C697374                   # "list"
         *    9F                            # array(*)
         *       61                         # text(1)
         *          61                      # "a"
         *       61                         # text(1)
         *          62                      # "b"
         *       FF                         # primitive(*)
         *    63                            # text(3)
         *       6D6170                     # "map"
         *    BF                            # map(*)
         *       01                         # unsigned(1)
         *       F5                         # primitive(21)
         *       02                         # unsigned(2)
         *       F4                         # primitive(20)
         *       FF                         # primitive(*)
         *    65                            # text(5)
         *       696E6E6572                 # "inner"
         *    A1                            # map(1)
         *       61                         # text(1)
         *          61                      # "a"
         *       63                         # text(3)
         *          6C6F6C                  # "lol"
         *    6A                            # text(10)
         *       696E6E6572734C697374       # "innersList"
         *    9F                            # array(*)
         *       A1                         # map(1)
         *          61                      # text(1)
         *             61                   # "a"
         *          63                      # text(3)
         *             6B656B               # "kek"
         *       FF                         # primitive(*)
         *    6A                            # text(10)
         *       62797465537472696E67       # "byteString"
         *    42                            # bytes(2)
         *       CAFE                       # "\xCA\xFE"
         *    69                            # text(9)
         *       627974654172726179         # "byteArray"
         *    9F                            # array(*)
         *       38 35                      # negative(53)
         *       21                         # negative(1)
         *       FF                         # primitive(*)
         */
        assertEquals(
            "a9637374726d48656c6c6f2c20776f726c64216169182a686e756c6c61626c65f6646c6973749f61616162ff636d6170bf01f502f4ff65696e6e6572a16161636c6f6c6a696e6e6572734c6973749fa16161636b656bff6a62797465537472696e6742cafe696279746541727261799f383521ff",
            Cbor.encodeToHexString(TypesUmbrella.serializer(), test)
        )
    }

    @Test
    fun writeManyNumbers() {
        val test = NumberTypesUmbrella(
            100500,
            Long.MAX_VALUE,
            42.0f,
            1235621356215.0,
            true,
            'a'
        )
        /**
         * A6                     # map(6)
         *    63                  # text(3)
         *       696E74           # "int"
         *    1A 00018894         # unsigned(100500)
         *    64                  # text(4)
         *       6C6F6E67         # "long"
         *    1B 7FFFFFFFFFFFFFFF # unsigned(9223372036854775807)
         *    65                  # text(5)
         *       666C6F6174       # "float"
         *    FA 42280000         # primitive(1109917696)
         *    66                  # text(6)
         *       646F75626C65     # "double"
         *    FB 4271FB0C5A2B7000 # primitive(4787883909342523392)
         *    67                  # text(7)
         *       626F6F6C65616E   # "boolean"
         *    F5                  # primitive(21)
         *    64                  # text(4)
         *       63686172         # "char"
         *    18 61               # unsigned(97)
         */
        assertEquals(
            "a663696e741a00018894646c6f6e671b7fffffffffffffff65666c6f6174fa4228000066646f75626c65fb4271fb0c5a2b700067626f6f6c65616ef564636861721861",
            Cbor.encodeToHexString(NumberTypesUmbrella.serializer(), test)
        )
    }

    @Test
    fun testWriteByteStringWhenNullable() {
        /**
         * A1                         # map(1)
         *    6A                      # text(10)
         *       62797465537472696E67 # "byteString"
         *    44                      # bytes(4)
         *       01020304             # "\u0001\u0002\u0003\u0004"
         */
        assertEquals(
            expected = "a16a62797465537472696e674401020304",
            actual = Cbor.encodeToHexString(
                serializer = NullableByteString.serializer(),
                value = NullableByteString(byteString = byteArrayOf(1, 2, 3, 4))
            )
        )

        /**
         * A1                         # map(1)
         *    6A                      # text(10)
         *       62797465537472696E67 # "byteString"
         *    40                      # bytes(0)
         *                            # ""
         */
        assertEquals(
            expected = "a16a62797465537472696e6740",
            actual = Cbor.encodeToHexString(
                serializer = NullableByteString.serializer(),
                value = NullableByteString(byteString = byteArrayOf())
            )
        )
    }

    @Test
    fun testWriteNullForNullableByteString() {
        /**
         * A1                         # map(1)
         *    6A                      # text(10)
         *       62797465537472696E67 # "byteString"
         *    F6                      # primitive(22)
         */
        assertEquals(
            expected = "a16a62797465537472696e67f6",
            actual = Cbor.encodeToHexString(
                serializer = NullableByteString.serializer(),
                value = NullableByteString(byteString = null)
            )
        )
    }

    @Test
    fun testWriteCustomByteString() {
        /**
         * A1           # map(1)
         *    61        # text(1)
         *       78     # "x"
         *    43        # bytes(3)
         *       112233 # "\u0011\"3"
         */
        assertEquals(
            expected = "a1617843112233",
            actual = Cbor.encodeToHexString(TypeWithCustomByteString(CustomByteString(0x11, 0x22, 0x33)))
        )
    }

    @Test
    fun testWriteNullableCustomByteString() {
        /**
         * A1           # map(1)
         *    61        # text(1)
         *       78     # "x"
         *    43        # bytes(3)
         *       112233 # "\u0011\"3"
         */
        assertEquals(
            expected = "a1617843112233",
            actual = Cbor.encodeToHexString(TypeWithNullableCustomByteString(CustomByteString(0x11, 0x22, 0x33)))
        )
    }

    @Test
    fun testWriteNullCustomByteString() {
        /**
         * A1       # map(1)
         *    61    # text(1)
         *       78 # "x"
         *    F6    # primitive(22)
         */
        assertEquals(
            expected = "a16178f6",
            actual = Cbor.encodeToHexString(TypeWithNullableCustomByteString(null))
        )
    }
}
