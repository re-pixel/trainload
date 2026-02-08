package trainload

/**
 * Usage:
 *     ./gradlew run < input.txt
 */
fun main() {
    val graph    = parseRailwayGraph(System.`in`.bufferedReader())
    val analysis = CargoAnalysis(graph)
    val results  = analysis.analyze()

    for (sid in graph.stations.keys.sorted()) {
        val cargo = results[sid]!!.sorted()
        if (cargo.isEmpty()) {
            println("$sid:")
        } else {
            println("$sid: ${cargo.joinToString(" ")}")
        }
    }
}
