package trainload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stress tests and special scenarios for CargoAnalysis.
 * These tests cover performance, large inputs, and unusual patterns.
 */
class CargoAnalysisStressTest {

    private fun runAnalysis(input: String): Map<Int, Set<Int>> {
        val graph = parseRailwayGraph(input.trimIndent().byteInputStream().bufferedReader())
        return CargoAnalysis(graph).analyze()
    }

    // ============================================================================
    // PERFORMANCE AND SCALE TESTS
    // ============================================================================

    /**
     * Wide branching - one station connects to many
     */
    @Test
    fun wideBranchingFanOut() {
        val fanOutSize = 20
        val stations = buildList {
            add("1 0 100")
            for (i in 2..fanOutSize + 1) {
                add("$i 100 ${100 + i}")
            }
        }.joinToString("\n")
        
        val edges = (2..fanOutSize + 1).joinToString("\n") { "1 $it" }
        
        val input = """
            ${fanOutSize + 1} $fanOutSize
            $stations
            $edges
            1
        """.trimIndent()
        
        val result = runAnalysis(input)
        
        assertEquals(emptySet(), result[1])
        for (i in 2..fanOutSize + 1) {
            assertEquals(setOf(100), result[i], "station $i")
        }
    }

    /**
     * Wide convergence - many stations connect to one
     */
    @Test
    fun wideConvergenceFanIn() {
        val fanInSize = 20
        val finalStation = fanInSize + 1
        
        val stations = buildList {
            add("1 0 100")
            for (i in 2..fanInSize) {
                add("$i 100 ${100 + i}")
            }
            add("$finalStation 0 999")
        }.joinToString("\n")
        
        val edges = buildList {
            for (i in 2..fanInSize) add("1 $i")
            for (i in 2..fanInSize) add("$i $finalStation")
        }.joinToString("\n")
        
        val edgeCount = (fanInSize - 1) * 2
        val input = """
            $finalStation $edgeCount
            $stations
            $edges
            1
        """.trimIndent()
        
        val result = runAnalysis(input)
        
        assertEquals(emptySet(), result[1])
        // Final station should see cargo from all paths
        val expectedCargo = (2..fanInSize).map { 100 + it }.toSet()
        assertEquals(expectedCargo, result[finalStation])
    }

    /**
     * Dense graph - many interconnections
     */
    @Test
    fun denseGraph() {
        val n = 10
        val stations = (1..n).joinToString("\n") { "$it 0 ${it * 10}" }
        
        // Create many edges (but not complete graph to keep test reasonable)
        val edges = buildList {
            for (i in 1..n) {
                for (j in 1..n) {
                    if (i != j && (i + j) % 3 == 0) { // Connect based on pattern
                        add("$i $j")
                    }
                }
            }
        }.joinToString("\n")
        
        val edgeCount = edges.split("\n").size
        val input = """
            $n $edgeCount
            $stations
            $edges
            1
        """.trimIndent()
        
        val result = runAnalysis(input)

        val reachableFromOne = setOf(1, 2, 4, 5, 7, 8, 10) 

        result.forEach { (station, cargo) ->
            if (station in reachableFromOne) {
                // Station 1 might be empty if there's no cycle back to it, 
                // but in this graph 1+2=3, 2+1=3, so 1 <-> 2 is a cycle.
                assertTrue(cargo.isNotEmpty(), "Reachable station $station should have cargo")
            } else {
                assertTrue(cargo.isEmpty(), "Unreachable station $station should be empty")
            }
        }
    }

    /**
     * Many cargo types in single chain
     */
    @Test
    fun manyCargoTypes() {
        val n = 30
        val stations = (1..n).joinToString("\n") { i ->
            val unload = if (i == 1) 0 else (i - 1) * 100
            val load = i * 100
            "$i $unload $load"
        }
        val edges = (1 until n).joinToString("\n") { "$it ${it + 1}" }
        
        val input = """
            $n ${n - 1}
            $stations
            $edges
            1
        """.trimIndent()
        
        val result = runAnalysis(input)
        
        assertEquals(emptySet(), result[1])
        for (i in 2..n) {
            assertEquals(setOf((i - 1) * 100), result[i], "station $i")
        }
    }

    /**
     * Deep cycle with many iterations needed
     */
    @Test
    fun deepCycleMultipleIterations() {
        val n = 15
        val stations = (1..n).joinToString("\n") { "$it 0 ${it * 10}" }
        val edges = buildList {
            for (i in 1 until n) add("$i ${i + 1}")
            add("$n 1") // Close the cycle
        }.joinToString("\n")
        
        val input = """
            $n $n
            $stations
            $edges
            1
        """.trimIndent()
        
        val result = runAnalysis(input)
        
        // All cargo should eventually reach all stations
        val allCargo = (1..n).map { it * 10 }.toSet()
        for (i in 1..n) {
            assertEquals(allCargo, result[i], "station $i")
        }
    }

    // ============================================================================
    // SPECIAL PATTERNS
    // ============================================================================

    /**
     * Hourglass pattern
     * 
     * Graph:  1   2
     *          \ /
     *           3
     *          / \
     *         4   5
     */
    @Test
    fun hourglassPattern() {
        val result = runAnalysis("""
            5 4
            1 0 10
            2 0 20
            3 0 30
            4 0 40
            5 0 50
            1 3
            2 3
            3 4
            3 5
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(emptySet(), result[2]) // Not reachable from 1
        assertEquals(setOf(10), result[3])
        assertEquals(setOf(10, 30), result[4])
        assertEquals(setOf(10, 30), result[5])
    }

    /**
     * Parallel chains merging
     * 
     * Graph: 1 → 2 → 3
     *             ↓   ↓
     *        4 → 5 → 6
     *             ↓
     *             7
     */
    @Test
    fun parallelChainsMerging() {
        val result = runAnalysis("""
            7 7
            1 0 1
            2 0 2
            3 0 3
            4 0 4
            5 0 5
            6 0 6
            7 0 7
            1 2
            2 3
            2 5
            3 6
            4 5
            5 6
            5 7
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(1), result[2])
        assertEquals(setOf(1, 2), result[3])
        assertEquals(emptySet(), result[4]) // Not reachable
        assertEquals(setOf(1, 2), result[5])
        assertEquals(setOf(1, 2, 3, 5), result[6])
        assertEquals(setOf(1, 2, 5), result[7])
    }

    /**
     * Ladder graph pattern
     * 
     * Graph: 1 → 2 → 3
     *        ↓   ↓   ↓
     *        4 → 5 → 6
     */
    @Test
    fun ladderPattern() {
        val result = runAnalysis("""
            6 6
            1 0 10
            2 0 20
            3 0 30
            4 0 40
            5 0 50
            6 0 60
            1 2
            2 3
            1 4
            4 5
            2 5
            3 6
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(10, 20), result[3])
        assertEquals(setOf(10), result[4])
        assertEquals(setOf(10, 20, 40), result[5])
        assertEquals(setOf(10, 20, 30), result[6])
    }

    /**
     * Multiple entry points to cycle
     * 
     * Graph:  1 → 3 → 4 → 3 (cycle)
     *         ↓
     *         2 → 3
     */
    @Test
    fun multipleEntryPointsToCycle() {
        val result = runAnalysis("""
            4 5
            1 0 10
            2 0 20
            3 0 30
            4 0 40
            1 2
            1 3
            2 3
            3 4
            4 3
            1
        """)

        val expected = setOf(10, 20, 30, 40)
        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(expected, result[3])
        assertEquals(expected, result[4])
    }

    /**
     * Butterfly pattern (two diamonds sharing center)
     * 
     * Graph:  1     5
     *         |\   /|
     *         | \ / |
     *         2  3  6
     *         | / \ |
     *         |/   \|
     *         4     7
     */
    @Test
    fun butterflyPattern() {
        val result = runAnalysis("""
            7 8
            1 0 10
            2 0 20
            3 0 30
            4 0 40
            5 0 50
            6 0 60
            7 0 70
            1 2
            1 3
            2 4
            3 4
            5 3
            5 6
            3 7
            6 7
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(10), result[3])
        assertEquals(setOf(10, 20, 30), result[4])
        assertEquals(emptySet(), result[5]) // Not reachable
        assertEquals(emptySet(), result[6]) // Not reachable
        assertEquals(setOf(10, 30), result[7])
    }

    // ============================================================================
    // SPECIAL CARGO BEHAVIOR TESTS
    // ============================================================================

    /**
     * Multiple stations unload same cargo
     * 
     * Graph: 1 → 2 → 3 → 4
     * 
     * Stations 2 and 3 both unload cargo 10
     */
    @Test
    fun multipleStationsUnloadSameCargo() {
        val result = runAnalysis("""
            4 3
            1 0 10
            2 10 20
            3 10 30
            4 0 40
            1 2
            2 3
            3 4
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(20), result[3]) // 10 was unloaded at 2
        assertEquals(setOf(20, 30), result[4])
    }

    /**
     * Cargo appears and disappears in cycle
     * 
     * Graph: 1 → 2 → 3 → 4 → 2 (cycle back to 2)
     */
    @Test
    fun cargoAppearDisappearCycle() {
        val result = runAnalysis("""
            4 4
            1 0 10
            2 10 20
            3 20 30
            4 30 40
            1 2
            2 3
            3 4
            4 2
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10, 40), result[2])
        assertEquals(setOf(20, 40), result[3])
        assertEquals(setOf(30, 40), result[4])
    }

    /**
     * Same cargo loaded at multiple stations
     * 
     * Graph: 1 → 2 → 3
     * 
     * Stations 1 and 2 both load cargo 10
     */
    @Test
    fun sameCargoLoadedMultipleTimes() {
        val result = runAnalysis("""
            3 2
            1 0 10
            2 0 10
            3 10 30
            1 2
            2 3
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(10), result[3])
    }

    /**
     * Cargo oscillates between two types
     * 
     * Graph: 1 → 2 → 3 → 4 → 5
     */
    @Test
    fun cargoOscillation() {
        val result = runAnalysis("""
            5 4
            1 0 1
            2 1 2
            3 2 1
            4 1 2
            5 2 1
            1 2
            2 3
            3 4
            4 5
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(1), result[2])
        assertEquals(setOf(2), result[3])
        assertEquals(setOf(1), result[4])
        assertEquals(setOf(2), result[5])
    }

    // ============================================================================
    // EDGE CASE VARIATIONS
    // ============================================================================

    /**
     * All stations have self-loops
     */
    @Test
    fun allStationsHaveSelfLoops() {
        val result = runAnalysis("""
            3 5
            1 0 10
            2 10 20
            3 20 30
            1 1
            2 2
            3 3
            1 2
            2 3
            1
        """)

        assertEquals(setOf(10), result[1])
        assertEquals(setOf(10, 20), result[2])
        assertEquals(setOf(20, 30), result[3])
    }

    /**
     * Multiple edges between same pair of stations
     * (This tests if parser handles duplicates correctly)
     */
    @Test
    fun multipleEdgesSameStations() {
        val result = runAnalysis("""
            2 3
            1 0 10
            2 10 20
            1 2
            1 2
            1 2
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
    }

    /**
     * Chain where each station doubles cargo count
     * (Tests set behavior - sets don't have duplicates)
     */
    @Test
    fun cargoIdentityPreservation() {
        val result = runAnalysis("""
            4 3
            1 0 10
            2 0 10
            3 0 10
            4 0 20
            1 2
            2 3
            3 4
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(10), result[3])
        assertEquals(setOf(10), result[4])
    }

    /**
     * Very simple cycle with immediate return
     * 
     * Graph: 1 → 2 → 1
     */
    @Test
    fun simplestCycle() {
        val result = runAnalysis("""
            2 2
            1 0 10
            2 0 20
            1 2
            2 1
            1
        """)

        assertEquals(setOf(10, 20), result[1])
        assertEquals(setOf(10, 20), result[2])
    }

    /**
     * Station loads cargo with very high ID
     */
    @Test
    fun extremelyLargeCargoId() {
        val result = runAnalysis("""
            2 1
            1 0 2147483647
            2 2147483647 2147483646
            1 2
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(2147483647), result[2])
    }
}