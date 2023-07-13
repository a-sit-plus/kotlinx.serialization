package kotlinx.serialization.cbor

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.test.*

class CborClassDefiniteMapTest {

    private val reference1 = ClassWith1Member("foo")
    private val reference2 = ClassWith2Member("foo", "bar")
    private val reference3 = ClassWith3Member("foo", "bar", "baz")
    private val referenceNullable = ClassWithNullableMembers(member3 = "foo")

    /**
     * A1                   # map(1)
     *    67                # text(7)
     *       6D656D62657231 # "member1"
     *    63                # text(3)
     *       666F6F         # "foo"
     */
    private val reference1Hex = "a1676d656d6265723163666f6f"

    /**
     * A2                   # map(2)
     *    67                # text(7)
     *       6D656D62657231 # "member1"
     *    63                # text(3)
     *       666F6F         # "foo"
     *    67                # text(7)
     *       6D656D62657232 # "member2"
     *    63                # text(3)
     *       626172         # "bar"
     *
     */
    private val reference2Hex = "a2676d656d6265723163666f6f676d656d6265723263626172"

    /**
     * A3                   # map(3)
     *    67                # text(7)
     *       6D656D62657231 # "member1"
     *    63                # text(3)
     *       666F6F         # "foo"
     *    67                # text(7)
     *       6D656D62657232 # "member2"
     *    63                # text(3)
     *       626172         # "bar"
     *    67                # text(7)
     *       6D656D62657233 # "member3"
     *    63                # text(3)
     *       62617A         # "baz"
     *
     */
    private val reference3Hex = "a3676d656d6265723163666f6f676d656d6265723263626172676d656d626572336362617a"

    /**
     * A1                   # map(1)
     *    67                # text(7)
     *       6D656D62657233 # "member3"
     *    63                # text(3)
     *       666F6F         # "foo"
     */
    private val referenceNullableHex = "a1676d656d6265723363666f6f"

    @Test
    fun writeReadVerifyReference1() {
        assertEquals(reference1Hex, Cbor.encodeToHexString(ClassWith1Member.serializer(), reference1))
        assertEquals(reference1, Cbor.decodeFromHexString(reference1Hex))
    }

    @Test
    fun writeReadVerifyReference2() {
        assertEquals(reference2Hex, Cbor.encodeToHexString(ClassWith2Member.serializer(), reference2))
        assertEquals(reference2, Cbor.decodeFromHexString(reference2Hex))
    }

    @Test
    fun writeReadVerifyReference3() {
        assertEquals(reference3Hex, Cbor.encodeToHexString(ClassWith3Member.serializer(), reference3))
        assertEquals(reference3, Cbor.decodeFromHexString(reference3Hex))
    }

    @Test
    fun writeReadVerifyNullable() {
        assertEquals(referenceNullableHex, Cbor.encodeToHexString(ClassWithNullableMembers.serializer(), referenceNullable))
        assertEquals(referenceNullable, Cbor.decodeFromHexString(referenceNullableHex))
    }

    @Serializable
    data class ClassWith1Member(
        @SerialName("member1")
        val member1: String,
    )

    @Serializable
    data class ClassWith2Member(
        @SerialName("member1")
        val member1: String,
        @SerialName("member2")
        val member2: String,
    )

    @Serializable
    data class ClassWith3Member(
        @SerialName("member1")
        val member1: String,
        @SerialName("member2")
        val member2: String,
        @SerialName("member3")
        val member3: String,
    )

    @Serializable
    data class ClassWithNullableMembers(
        @SerialName("member1")
        val member1: String? = null,
        @SerialName("member2")
        val member2: String? = null,
        @SerialName("member3")
        val member3: String,
    )

}
