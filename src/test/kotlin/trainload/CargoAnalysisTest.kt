package trainload

import kotlin.test.Test
import kotlin.test.assertEquals

class CargoAnalysisTest {

    private fun runAnalysis(input: String): Map<Int, Set<Int>> {
        val graph = parseRailwayGraph(input.trimIndent().byteInputStream().bufferedReader())
        return CargoAnalysis(graph).analyze()
    }

    /**
     * Simple linear chain: 1 → 2 → 3
     *
     *   Station 1: unload=10, load=20
     *   Station 2: unload=30, load=40
     *   Station 3: unload=20, load=50
     *
     * Expected arrivals:
     *   1: {}            
     *   2: {20}          
     *   3: {20, 40}      
     *
     */
    @Test
    fun linearChain() {
        val result = runAnalysis("""
            3 2
            1 10 20
            2 30 40
            3 20 50
            1 2
            2 3
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(20), result[2])
        assertEquals(setOf(20, 40), result[3])
    }

    /**
     * Simple cycle: 1 → 2 → 1
     *
     *   Station 1: unload=1, load=2
     *   Station 2: unload=3, load=4
     *
     * First pass:  start@1 {} → load 2 → arrive@2 {2} → load 4 → {2,4}
     *              → arrive@1 {2,4} → unload 1 → load 2 → {2,4}
     * Second pass: arrive@2 {2,4} → unload 3 → load 4 → {2,4}
     *              fixpoint reached.
     *
     * Expected arrivals:
     *   1: {2, 4}   
     *   2: {2, 4}   
     */
    @Test
    fun simpleCycle() {
        val result = runAnalysis("""
            2 2
            1 1 2
            2 3 4
            1 2
            2 1
            1
        """)

        assertEquals(setOf(2, 4), result[1])
        assertEquals(setOf(2, 4), result[2])
    }

    /**
     * Four-station ring: 1 → 2 → 3 → 4 → 1
     *
     * Each station unloads one type and loads another.  Cargo accumulates
     * around the cycle until the fixpoint stabilises.
     *
     *   Station 1: unload=1, load=2
     *   Station 2: unload=3, load=4
     *   Station 3: unload=2, load=1
     *   Station 4: unload=4, load=3
     *
     * First lap:
     *   1: {} → load 2 → {2}
     *   2: {2} → unload 3 → load 4 → {2,4}
     *   3: {2,4} → unload 2 → {4} → load 1 → {1,4}
     *   4: {1,4} → unload 4 → {1} → load 3 → {1,3}
     *   back to 1: {1,3} → unload 1 → {3} → load 2 → {2,3}
     *
     * Second lap:
     *   2: {2,3} → unload 3 → {2} → load 4 → {2,4}
     *   fixpoint.
     *
     * Expected arrivals:
     *   1: {1, 3}
     *   2: {2, 3}
     *   3: {2, 4}
     *   4: {1, 4}
     */
    @Test
    fun fourStationRing() {
        val result = runAnalysis("""
            4 4
            1 1 2
            2 3 4
            3 2 1
            4 4 3
            1 2
            2 3
            3 4
            4 1
            1
        """)

        assertEquals(setOf(1, 3), result[1])
        assertEquals(setOf(2, 3), result[2])
        assertEquals(setOf(2, 4), result[3])
        assertEquals(setOf(1, 4), result[4])
    }

    /**
     * Diamond with diverging paths:
     *
     *       1
     *      / \
     *     2   3
     *      \ /
     *       4
     *
     *   Station 1: unload=0, load=10
     *   Station 2: unload=10, load=20  (removes 10, adds 20)
     *   Station 3: unload=99, load=30  (keeps 10, adds 30)
     *   Station 4: unload=0, load=40
     *
     * Path 1→2→4: arrive@2 {10}, depart {20}; arrive@4 {20}
     * Path 1→3→4: arrive@3 {10}, depart {10,30}; arrive@4 {10,30}
     *
     * Join at 4: {20} ∪ {10,30} = {10, 20, 30}
     */
    @Test
    fun diamondDivergence() {
        val result = runAnalysis("""
            4 4
            1 0 10
            2 10 20
            3 99 30
            4 0 40
            1 2
            1 3
            2 4
            3 4
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(10), result[3])
        assertEquals(setOf(10, 20, 30), result[4])
    }

    @Test
    fun longChainWithLargeCargoIds() {
        val stationCount = 50
        val stations = (1..stationCount).joinToString("\n") { i ->
            val unload = if (i == 1) 0 else 1000 + i - 1
            val load = 1000 + i
            "$i $unload $load"
        }
        val edges = (1 until stationCount).joinToString("\n") { "$it ${it + 1}" }
        val input = """
            $stationCount ${stationCount - 1}
            $stations
            $edges
            1
        """.trimIndent()
        val result = runAnalysis(input)
        assertEquals(emptySet<Int>(), result[1])
        for (i in 2..stationCount) {
            assertEquals(setOf(1000 + i - 1), result[i], "station $i")
        }
    }

    @Test
    fun twoLongChainsMergingWithLargeCargoIds() {
        val n = 25
        val stations = buildList {
            add("1 0 1000")
            for (i in 2..n) add("$i ${1000 + i - 2} ${1000 + i - 1}")
            add("${n + 1} 0 2000")
            for (i in n + 2..2 * n) add("$i ${2000 + i - n - 2} ${2000 + i - n - 1}")
            add("${2 * n + 1} 0 3000")
        }.joinToString("\n")
        val edges = buildList {
            for (i in 1 until n) add("$i ${i + 1}")
            add("$n ${2 * n + 1}")
            add("1 ${n + 1}")
            for (i in n + 1 until 2 * n) add("$i ${i + 1}")
            add("${2 * n} ${2 * n + 1}")
        }.joinToString("\n")
        val edgeCount = 2 * (n - 1) + 3
        val input = """
            ${2 * n + 1} $edgeCount
            $stations
            $edges
            1
        """.trimIndent()
        val result = runAnalysis(input)
        assertEquals(emptySet<Int>(), result[1])
        for (i in 2..n) assertEquals(setOf(1000 + i - 2), result[i], "station $i")
        assertEquals(setOf(1000), result[n + 1])
        for (i in n + 2..2 * n) assertEquals(setOf(1000, 2000 + i - n - 2), result[i], "station $i")
        assertEquals(setOf(1000, 1000 + n - 1, 2000 + n - 1), result[2 * n + 1])
    }
}
