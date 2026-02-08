package trainload

data class Station(val id: Int, val unload: Int, val load: Int)

data class RailwayGraph(
    val stations: Map<Int, Station>,
    val successors: Map<Int, List<Int>>,
    val predecessors: Map<Int, List<Int>>,
    val startStation: Int
)

fun parseRailwayGraph(reader: java.io.BufferedReader): RailwayGraph {
    val firstLine = reader.readLine().trim().split("\\s+".toRegex()).map { it.toInt() }
    val stationCount = firstLine[0]
    val trackCount = firstLine[1]

    val stations = mutableMapOf<Int, Station>()
    repeat(stationCount) {
        val parts = reader.readLine().trim().split("\\s+".toRegex()).map { it.toInt() }
        stations[parts[0]] = Station(parts[0], parts[1], parts[2])
    }

    val successors = mutableMapOf<Int, MutableList<Int>>()
    val predecessors = mutableMapOf<Int, MutableList<Int>>()
    repeat(trackCount) {
        val parts = reader.readLine().trim().split("\\s+".toRegex()).map { it.toInt() }
        val from = parts[0]
        val to = parts[1]
        successors.getOrPut(from) { mutableListOf() }.add(to)
        predecessors.getOrPut(to) { mutableListOf() }.add(from)
    }

    val startStation = reader.readLine().trim().toInt()

    return RailwayGraph(stations, successors, predecessors, startStation)
}
