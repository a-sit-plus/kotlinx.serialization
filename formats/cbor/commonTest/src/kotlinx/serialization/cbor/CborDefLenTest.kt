/*
 * Copyright 2017-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.serialization.cbor

import kotlinx.serialization.*
import kotlin.test.*


class CborDefLenTest {
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

        println("\n\nENCODE DEFAULTS=TRUE, ALL SET")
        assertEquals(
            "a9637374726d48656c6c6f2c20776f726c64216169182a686e756c6c61626c65f6646c6973748261616162636d6170a201f502f465696e6e6572a16161636c6f6c6a696e6e6572734c69737481a16161636b656b6a62797465537472696e6742cafe6962797465417272617982383521",
            Cbor {
                writeDefiniteLengths = true
                encodeDefaults = true
            }.encodeToHexString(TypesUmbrella.serializer(), test)
        )

        println("\n\nENCODE DEFAULTS=FALSE, ALL SET")
        Cbor {
            writeDefiniteLengths = true
            encodeDefaults = false
        }.encodeToHexString(TypesUmbrella.serializer(), test)



        println("\n\nENCODE DEFAULTS=TRUE, DEFAULT NOT SET")
        Cbor {
            writeDefiniteLengths = true
            this.encodeDefaults = true
        }.encodeToHexString(
            TypesUmbrella.serializer(), TypesUmbrella(
                "Hello, world!",
                42,
                list = listOf("a", "b"),
                map = mapOf(1 to true, 2 to false),
                inner = Simple("lol"),
                innersList = listOf(Simple("kek")),
                byteString = HexConverter.parseHexBinary("cafe"),
                byteArray = HexConverter.parseHexBinary("cafe")
            )
        )

        println("\n\nENCODE DEFAULTS=FALSE, DEFAULT NOT SET")
        Cbor {
            writeDefiniteLengths = true
            this.encodeDefaults = false
        }.encodeToHexString(
            TypesUmbrella.serializer(), TypesUmbrella(
                "Hello, world!",
                42,
                list = listOf("a", "b"),
                map = mapOf(1 to true, 2 to false),
                inner = Simple("lol"),
                innersList = listOf(Simple("kek")),
                byteString = HexConverter.parseHexBinary("cafe"),
                byteArray = HexConverter.parseHexBinary("cafe")
            )
        )
    }

}