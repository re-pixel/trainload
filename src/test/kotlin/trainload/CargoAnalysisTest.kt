package trainload

import kotlin.test.Test
import kotlin.test.assertEquals

class CargoAnalysisTest {

    private fun runAnalysis(input: String): Map<Int, Set<Int>> {
        val graph = parseRailwayGraph(input.trimIndent().byteInputStream().bufferedReader())
        return CargoAnalysis(graph).analyze()
    }

    // ============================================================================
    // CARGO ACCUMULATION TESTS
    // ============================================================================

    /**
     * Cargo accumulation without unloading
     * 
     * Graph: 1 → 2 → 3 → 4
     * 
     * Station 1: unload=0, load=10
     * Station 2: unload=99, load=20  (doesn't unload anything, adds 20)
     * Station 3: unload=99, load=30  (doesn't unload anything, adds 30)
     * Station 4: unload=99, load=40
     * 
     * Expected: cargo accumulates continuously
     *   1: {}
     *   2: {10}
     *   3: {10, 20}
     *   4: {10, 20, 30}
     */
    @Test
    fun continuousAccumulation() {
        val result = runAnalysis("""
            4 3
            1 0 10
            2 99 20
            3 99 30
            4 99 40
            1 2
            2 3
            3 4
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(10, 20), result[3])
        assertEquals(setOf(10, 20, 30), result[4])
    }

    /**
     * Station loads same cargo it unloads
     * 
     * Graph: 1 → 2 → 3
     * 
     * Station 1: unload=0, load=10
     * Station 2: unload=10, load=10  (unloads 10, then immediately loads 10)
     * Station 3: unload=10, load=30
     * 
     * Expected:
     *   1: {}
     *   2: {10}
     *   3: {10}  (cargo 10 persists through station 2)
     */
    @Test
    fun stationReloadsWhatItUnloads() {
        val result = runAnalysis("""
            3 2
            1 0 10
            2 10 10
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
     * Partial unloading - removes one cargo but keeps others
     * 
     * Graph: 1 → 2 → 3 → 4
     * 
     * Station 1: unload=0, load=10
     * Station 2: unload=0, load=20
     * Station 3: unload=10, load=30  (removes 10, keeps 20, adds 30)
     * Station 4: unload=0, load=40
     * 
     * Expected:
     *   1: {}
     *   2: {10}
     *   3: {10, 20}
     *   4: {20, 30}  (10 was removed at station 3)
     */
    @Test
    fun partialUnloading() {
        val result = runAnalysis("""
            4 3
            1 0 10
            2 0 20
            3 10 30
            4 0 40
            1 2
            2 3
            3 4
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(10, 20), result[3])
        assertEquals(setOf(20, 30), result[4])
    }

    // ============================================================================
    // BRANCHING AND MERGING TESTS
    // ============================================================================

    /**
     * Simple branching paths from start
     * 
     * Graph:     2
     *           /
     *          1
     *           \
     *            3
     * 
     * Station 1: unload=0, load=10
     * Station 2: unload=10, load=20
     * Station 3: unload=10, load=30
     * 
     * Expected:
     *   1: {}
     *   2: {10}
     *   3: {10}
     */
    @Test
    fun simpleBranching() {
        val result = runAnalysis("""
            3 2
            1 0 10
            2 10 20
            3 10 30
            1 2
            1 3
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(10), result[3])
    }

    /**
     * Multiple paths to same destination with different cargo
     * 
     * Graph: 1 → 2 → 4
     *         \     /
     *          → 3 →
     * 
     * Station 1: unload=0, load=10
     * Station 2: unload=10, load=20
     * Station 3: unload=10, load=30
     * Station 4: unload=0, load=40
     * 
     * Path 1→2→4: arrive@4 with {20}
     * Path 1→3→4: arrive@4 with {30}
     * 
     * Expected:
     *   4: {20, 30}  (union of both paths)
     */
    @Test
    fun multiplePathsDifferentCargo() {
        val result = runAnalysis("""
            4 4
            1 0 10
            2 10 20
            3 10 30
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
        assertEquals(setOf(20, 30), result[4])
    }

    /**
     * Complex diamond with multiple cargo combinations
     * 
     * Graph:     2 → 4
     *           / \ /
     *          1   X
     *           \ / \
     *            3 → 5
     * 
     * Station 1: unload=0, load=10
     * Station 2: unload=0, load=20
     * Station 3: unload=0, load=30
     * Station 4: unload=0, load=40
     * Station 5: unload=0, load=50
     * 
     * Expected: station 4 and 5 see cargo from multiple paths
     *   4: {10, 20, 30}
     *   5: {10, 20, 30}
     */
    @Test
    fun complexDiamondPattern() {
        val result = runAnalysis("""
            5 6
            1 0 10
            2 0 20
            3 0 30
            4 0 40
            5 0 50
            1 2
            1 3
            2 4
            2 5
            3 4
            3 5
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(10), result[3])
        assertEquals(setOf(10, 20, 30), result[4])
        assertEquals(setOf(10, 20, 30), result[5])
    }

    // ============================================================================
    // CYCLE TESTS
    // ============================================================================

    /**
     * Self-loop at station
     * 
     * Graph: 1 → 2 → 2 (self-loop at 2)
     * 
     * Station 1: unload=0, load=10
     * Station 2: unload=10, load=20
     * 
     * First visit to 2: arrive with {10}, unload 10, load 20, have {20}
     * Self-loop: arrive with {20}, unload 10 (don't have), load 20, still {20}
     * 
     * Expected:
     *   1: {}
     *   2: {10, 20}  (can arrive with 10 or with 20)
     */
    @Test
    fun selfLoopStation() {
        val result = runAnalysis("""
            2 2
            1 0 10
            2 10 20
            1 2
            2 2
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10, 20), result[2])
    }

    /**
     * Cycle back to start station
     * 
     * Graph: 1 → 2 → 1
     * 
     * Station 1: unload=99, load=10
     * Station 2: unload=10, load=20
     * 
     * Initial: start@1 with {}
     * First pass: 1→2 arrive with {10}, depart with {20}
     *             2→1 arrive with {20}
     * 
     * Expected:
     *   1: {20}  (can arrive with 20 from cycle)
     *   2: {10, 20}
     */
    @Test
    fun cycleBackToStart() {
        val result = runAnalysis("""
            2 2
            1 99 10
            2 10 20
            1 2
            2 1
            1
        """)

        assertEquals(setOf(20), result[1])
        assertEquals(setOf(10, 20), result[2])
    }

    /**
     * Three-station cycle with cargo filtering
     * 
     * Graph: 1 → 2 → 3 → 1
     * 
     * Station 1: unload=0, load=10
     * Station 2: unload=10, load=20
     * Station 3: unload=20, load=30
     * 
     * First lap: 1:{} → 2:{10} → 3:{20} → 1:{30}
     * Second lap: 1:{30} → 2:{10,30} → 3:{20,30} → 1:{30}
     * Fixpoint reached.
     * 
     * Expected:
     *   1: {30}
     *   2: {10, 30}
     *   3: {20, 30}
     */
    @Test
    fun threeStationCycle() {
        val result = runAnalysis("""
            3 3
            1 0 10
            2 10 20
            3 20 30
            1 2
            2 3
            3 1
            1
        """)

        assertEquals(setOf(30), result[1])
        assertEquals(setOf(10, 30), result[2])
        assertEquals(setOf(20, 30), result[3])
    }

    /**
     * Long cycle with full accumulation
     * 
     * Graph: 1 → 2 → 3 → 4 → 1
     * 
     * All stations just load, no unloading (unload=0)
     * 
     * Expected: all cargo accumulates and cycles
     */
    @Test
    fun longCycleAccumulation() {
        val result = runAnalysis("""
            4 4
            1 0 10
            2 0 20
            3 0 30
            4 0 40
            1 2
            2 3
            3 4
            4 1
            1
        """)

        assertEquals(setOf(10, 20, 30, 40), result[1])
        assertEquals(setOf(10, 20, 30, 40), result[2])
        assertEquals(setOf(10, 20, 30, 40), result[3])
        assertEquals(setOf(10, 20, 30, 40), result[4])
    }

    /**
     * Nested cycles
     * 
     * Graph: 1 → 2 → 3 → 2 (inner cycle)
     *        ^         |
     *        |_________|  (outer cycle)
     * 
     * Station 1: unload=0, load=10
     * Station 2: unload=0, load=20
     * Station 3: unload=0, load=30
     */
    @Test
    fun nestedCycles() {
        val result = runAnalysis("""
            3 4
            1 0 10
            2 0 20
            3 0 30
            1 2
            2 3
            3 2
            3 1
            1
        """)

        // All cargo should eventually reach all stations
        assertEquals(setOf(10, 20, 30), result[1])
        assertEquals(setOf(10, 20, 30), result[2])
        assertEquals(setOf(10, 20, 30), result[3])
    }

    // ============================================================================
    // GRAPH STRUCTURE TESTS
    // ============================================================================

    /**
     * Binary tree structure
     * 
     * Graph:      1
     *           /   \
     *          2     3
     *         / \   / \
     *        4   5 6   7
     * 
     * Each station loads its own cargo type (1, 2, 3, etc.)
     */
    @Test
    fun binaryTreeStructure() {
        val result = runAnalysis("""
            7 6
            1 0 1
            2 0 2
            3 0 3
            4 0 4
            5 0 5
            6 0 6
            7 0 7
            1 2
            1 3
            2 4
            2 5
            3 6
            3 7
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(1), result[2])
        assertEquals(setOf(1), result[3])
        assertEquals(setOf(1, 2), result[4])
        assertEquals(setOf(1, 2), result[5])
        assertEquals(setOf(1, 3), result[6])
        assertEquals(setOf(1, 3), result[7])
    }

    /**
     * Star topology - center distributes to all
     * 
     * Graph:    2
     *          /
     *         1 - 3
     *          \
     *           4
     * 
     * Station 1: unload=0, load=10
     * Stations 2,3,4: each unload 10 and load their own cargo
     */
    @Test
    fun starTopology() {
        val result = runAnalysis("""
            4 3
            1 0 10
            2 10 20
            3 10 30
            4 10 40
            1 2
            1 3
            1 4
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(10), result[3])
        assertEquals(setOf(10), result[4])
    }

    /**
     * 2x2 grid topology
     * 
     * Graph: 1 → 2
     *        ↓   ↓
     *        3 → 4
     */
    @Test
    fun gridTopology() {
        val result = runAnalysis("""
            4 4
            1 0 1
            2 0 2
            3 0 3
            4 0 4
            1 2
            1 3
            2 4
            3 4
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(1), result[2])
        assertEquals(setOf(1), result[3])
        assertEquals(setOf(1, 2, 3), result[4])
    }

    /**
     * Disconnected components
     * 
     * Component 1: 1 → 2 → 3 (contains start)
     * Component 2: 4 → 5 (disconnected)
     * 
     * Expected: only component 1 is reachable
     */
    @Test
    fun disconnectedComponents() {
        val result = runAnalysis("""
            5 3
            1 0 10
            2 10 20
            3 20 30
            4 0 40
            5 40 50
            1 2
            2 3
            4 5
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(20), result[3])
        // Stations 4 and 5 are unreachable - arrive with empty cargo
        assertEquals(emptySet(), result[4])
        assertEquals(emptySet(), result[5])
    }

    // ============================================================================
    // EDGE CASES
    // ============================================================================

    /**
     * Single station with no outgoing edges
     */
    @Test
    fun singleStationNoEdges() {
        val result = runAnalysis("""
            1 0
            1 0 10
            1
        """)

        assertEquals(emptySet(), result[1])
    }

    /**
     * Two stations with no connection
     */
    @Test
    fun twoStationsNoConnection() {
        val result = runAnalysis("""
            2 0
            1 0 10
            2 0 20
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(emptySet(), result[2])
    }

    /**
     * Station with self-loop only (no other connections)
     * 
     * Graph: 1 → 1 (self-loop)
     */
    @Test
    fun selfLoopOnly() {
        val result = runAnalysis("""
            1 1
            1 0 10
            1 1
            1
        """)

        assertEquals(setOf(10), result[1])
    }

    /**
     * All stations use same cargo type
     * 
     * Graph: 1 → 2 → 3
     * 
     * All stations: unload=1, load=1
     */
    @Test
    fun allStationsSameCargo() {
        val result = runAnalysis("""
            3 2
            1 1 1
            2 1 1
            3 1 1
            1 2
            2 3
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(1), result[2])
        assertEquals(setOf(1), result[3])
    }

    /**
     * Alternating cargo replacement
     * 
     * Graph: 1 → 2 → 3 → 4 → 5
     * 
     * Each station unloads what previous loaded, loads next
     */
    @Test
    fun alternatingReplacement() {
        val result = runAnalysis("""
            5 4
            1 0 1
            2 1 2
            3 2 3
            4 3 4
            5 4 5
            1 2
            2 3
            3 4
            4 5
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(1), result[2])
        assertEquals(setOf(2), result[3])
        assertEquals(setOf(3), result[4])
        assertEquals(setOf(4), result[5])
    }

    /**
     * Zero cargo ID (edge case for cargo numbering)
     * 
     * Graph: 1 → 2
     */
    @Test
    fun zeroCargoId() {
        val result = runAnalysis("""
            2 1
            1 99 0
            2 0 1
            1 2
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(0), result[2])
    }

    /**
     * Negative cargo IDs
     * 
     * Graph: 1 → 2
     */
    @Test
    fun negativeCargoIds() {
        val result = runAnalysis("""
            2 1
            1 0 -1
            2 -1 -2
            1 2
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(-1), result[2])
    }

    /**
     * Very large cargo IDs
     */
    @Test
    fun veryLargeCargoIds() {
        val result = runAnalysis("""
            2 1
            1 0 1000000
            2 1000000 2000000
            1 2
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(1000000), result[2])
    }

    // ============================================================================
    // COMPLEX SCENARIOS
    // ============================================================================

    /**
     * Complete graph K4 (all stations connected to all others)
     * 
     * Eventually all cargo should reach all stations
     */
    @Test
    fun completeGraphK4() {
        val result = runAnalysis("""
            4 12
            1 0 10
            2 0 20
            3 0 30
            4 0 40
            1 2
            1 3
            1 4
            2 1
            2 3
            2 4
            3 1
            3 2
            3 4
            4 1
            4 2
            4 3
            1
        """)

        // All stations should eventually see all cargo
        val allCargo = setOf(10, 20, 30, 40)
        assertEquals(allCargo, result[1])
        assertEquals(allCargo, result[2])
        assertEquals(allCargo, result[3])
        assertEquals(allCargo, result[4])
    }

    /**
     * Multiple cycles sharing nodes
     * 
     * Graph: 1 → 2 → 3 → 1
     *             ↓   ↑
     *             4 →
     * 
     * Two cycles: 1-2-3-1 and 2-4-3-2
     */
    @Test
    fun multipleCyclesSharedNodes() {
        val result = runAnalysis("""
            4 5
            1 0 10
            2 0 20
            3 0 30
            4 0 40
            1 2
            2 3
            3 1
            2 4
            4 3
            1
        """)

        // All cargo should reach all stations due to cycles
        val allCargo = setOf(10, 20, 30, 40)
        assertEquals(allCargo, result[1])
        assertEquals(allCargo, result[2])
        assertEquals(allCargo, result[3])
        assertEquals(allCargo, result[4])
    }

    /**
     * Deep tree with accumulation at leaves
     * 
     * Graph: 1 → 2 → 3 → 4 → 5 (single long branch)
     */
    @Test
    fun deepChainAccumulation() {
        val result = runAnalysis("""
            5 4
            1 0 10
            2 0 20
            3 0 30
            4 0 40
            5 0 50
            1 2
            2 3
            3 4
            4 5
            1
        """)

        assertEquals(emptySet(), result[1])
        assertEquals(setOf(10), result[2])
        assertEquals(setOf(10, 20), result[3])
        assertEquals(setOf(10, 20, 30), result[4])
        assertEquals(setOf(10, 20, 30, 40), result[5])
    }
}