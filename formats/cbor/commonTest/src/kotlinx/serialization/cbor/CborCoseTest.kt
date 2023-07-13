package kotlinx.serialization.cbor

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.test.*

class CborCoseTest {

    private val reference = CoseSigned(
        protectedHeader = ByteStringWrapper(CoseHeader(alg = -7)),
        unprotectedHeader = CoseHeader(kid = "11"),
        payload = "This is the content.".encodeToByteArray(),
        signature = HexConverter.parseHexBinary("8EB33E4CA31D1C465AB05AAC34CC6B23D58FEF5C083106C4D25A91AEF0B0117E2AF9A291AA32E14AB834DC56ED2A223444547E01F11D3B0916E5A4C345CACB36")
    )

    /**
     * D2                                      # tag(18)
     *    84                                   # array(4)
     *       43                                # bytes(3)
     *          A10126                         # "\xA1\u0001&" (serialized map(1) unsigned(1) negative(6))
     *       A1                                # map(1)
     *          04                             # unsigned(4)
     *          42                             # bytes(2)
     *             3131                        # "11"
     *       54                                # bytes(20)
     *          546869732069732074686520636F6E74656E742E # "This is the content."
     *       58 40                             # bytes(64)
     *          8EB33E4CA31D1C465AB05AAC34CC6B23D58FEF5C083106C4D25A91AEF0B0117E2AF9A291AA32E14AB834DC56ED2A223444547E01F11D3B0916E5A4C345CACB36
     *
     *
     * OR
     *
     *
     * 18([h'A10126', {4: h'3131'}, h'546869732069732074686520636F6E74656E742E', h'8EB33E4CA31D1C465AB05AAC34CC6B23D58FEF5C083106C4D25A91AEF0B0117E2AF9A291AA32E14AB834DC56ED2A223444547E01F11D3B0916E5A4C345CACB36'])
     */
    private val referenceHexString = "d28443a10126a10462313154546869732069732074686520636f6e74656e" +
        "742e58408eb33e4ca31d1c465ab05aac34cc6b23d58fef5c083106c4d25a" +
        "91aef0b0117e2af9a291aa32e14ab834dc56ed2a223444547e01f11d3b09" +
        "16e5a4c345cacb36"

    @Test
    fun writeReadVerifyCoseSigned() {
        assertEquals(reference, Cbor.decodeFromHexString(CoseSigned.serializer(), referenceHexString))
        assertEquals(referenceHexString, Cbor.encodeToHexString(CoseSigned.serializer(), reference))
    }


    @Serializable
    data class CoseHeader(
        @SerialLabel(1)
        @SerialName("alg")
        val alg: Int? = null,
        @SerialLabel(4)
        @SerialName("kid")
        @ByteString
        val kid: String? = null,
    )

    @Serializable
    @CborArray(18U)
    data class CoseSigned(
        @Serializable(with = ByteStringWrapperSerializer::class)
        @ByteString
        val protectedHeader: ByteStringWrapper<CoseHeader>,
        val unprotectedHeader: CoseHeader? = null,
        @ByteString
        val payload: ByteArray,
        @ByteString
        val signature: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as CoseSigned

            if (protectedHeader != other.protectedHeader) return false
            if (unprotectedHeader != other.unprotectedHeader) return false
            if (!payload.contentEquals(other.payload)) return false
            return signature.contentEquals(other.signature)
        }

        override fun hashCode(): Int {
            var result = protectedHeader.hashCode()
            result = 31 * result + (unprotectedHeader?.hashCode() ?: 0)
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + signature.contentHashCode()
            return result
        }

    }

    object ByteStringWrapperSerializer : KSerializer<ByteStringWrapper<CoseHeader>> {

        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("ByteStringWrapperSerializer", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: ByteStringWrapper<CoseHeader>) {
            val bytes = Cbor.encodeToByteArray(value.value)
            encoder.encodeSerializableValue(ByteArraySerializer(), bytes)
        }

        override fun deserialize(decoder: Decoder): ByteStringWrapper<CoseHeader> {
            val bytes = decoder.decodeSerializableValue(ByteArraySerializer())
            return ByteStringWrapper(Cbor.decodeFromByteArray(bytes), bytes)
        }

    }

}