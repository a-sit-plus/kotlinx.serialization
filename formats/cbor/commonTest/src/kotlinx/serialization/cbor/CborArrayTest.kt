package kotlinx.serialization.cbor

import kotlinx.serialization.*
import kotlin.test.*


class CborArrayTest {

    @Test
    fun writeReadVerifyArraySize1() {
        val reference = ClassAs1Array(alg = -7)

        /**
         * 81    # array(1)
         *    26 # negative(6)
         */
        val referenceHexString = "8126"
        val cbor = Cbor

        assertEquals(referenceHexString, cbor.encodeToHexString(ClassAs1Array.serializer(), reference))
        assertEquals(reference, cbor.decodeFromHexString(ClassAs1Array.serializer(), referenceHexString))
    }

    @Test
    fun writeReadVerifyArraySize2() {
        val reference = ClassAs2Array(alg = -7, kid = "foo")

        /**
         * 82           # array(2)
         *    26        # negative(6)
         *    63        # text(3)
         *       666F6F # "foo"
         */
        val referenceHexString = "822663666f6f"
        val cbor = Cbor

        assertEquals(referenceHexString, cbor.encodeToHexString(ClassAs2Array.serializer(), reference))
        assertEquals(reference, cbor.decodeFromHexString(ClassAs2Array.serializer(), referenceHexString))
    }

    @Test
    fun writeReadVerifyClassWithArray() {
        val reference = ClassWithArray(array = ClassAs2Array(alg = -7, kid = "bar"))

        /**
         * BF               # map(*)
         *    65            # text(5)
         *       6172726179 # "array"
         *    82            # array(2)
         *       26         # negative(6)
         *       63         # text(3)
         *          626172  # "bar"
         *    FF            # primitive(*)
         */
        val referenceHexString = "bf656172726179822663626172ff"
        val cbor = Cbor

        assertEquals(referenceHexString, cbor.encodeToHexString(ClassWithArray.serializer(), reference))
        assertEquals(reference, cbor.decodeFromHexString(ClassWithArray.serializer(), referenceHexString))
    }

    @CborArray
    @Serializable
    data class ClassAs1Array(
        @SerialName("alg")
        val alg: Int,
    )

    @CborArray
    @Serializable
    data class ClassAs2Array(
        @SerialName("alg")
        val alg: Int,
        @SerialName("kid")
        val kid: String,
        @SerialName("nullable")
        val nullable: String? = null
    )

    @Serializable
    data class ClassWithArray(
        @SerialName("array")
        val array: ClassAs2Array,
    )
}

