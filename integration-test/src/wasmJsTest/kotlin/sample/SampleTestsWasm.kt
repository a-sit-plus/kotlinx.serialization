package sample

import kotlin.test.Test
import kotlin.test.assertTrue

class SampleTestsWasm {
    @Test
    fun testHello() {
        assertTrue("WasmJs" in hello())
    }
}
