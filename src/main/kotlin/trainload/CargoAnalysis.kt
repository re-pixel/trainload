package trainload

import java.util.BitSet

class CargoAnalysis(private val graph: RailwayGraph) {

    private val cargoToIndex: Map<Int, Int>
    private val indexToCargo: Map<Int, Int>
    private val nUniqueCargos: Int
    private val rpoStations: List<Int>
    private val stationToRpoIndex: Map<Int, Int>

    init {
        val sorted = graph.stations.values
            .flatMap { listOf(it.unload, it.load) }
            .distinct()
            .sorted()
        cargoToIndex = sorted.withIndex().associate { (i, c) -> c to i }
        indexToCargo = cargoToIndex.entries.associate { (k, v) -> v to k }
        nUniqueCargos = sorted.size
        rpoStations = computeRPO()
        stationToRpoIndex = rpoStations.withIndex().associate { it.value to it.index }
    }

    private fun computeRPO(): List<Int> {
        val visited = mutableSetOf<Int>()
        val postOrder = mutableListOf<Int>()

        
        fun dfs(u: Int) {
            visited.add(u)
            graph.successors[u]?.forEach { v -> if (v !in visited) dfs(v) }
            postOrder.add(u)
        }
        
        dfs(graph.startStation)
        return postOrder.reversed()
    }

    private fun transfer(station: Station, inSet: BitSet): BitSet {
        val out = inSet.clone() as BitSet
        cargoToIndex[station.unload]?.let { out.clear(it) }
        cargoToIndex[station.load]?.let { out.set(it) }
        return out
    }

    fun analyze(): Map<Int, Set<Int>> {
        val inSets  = graph.stations.keys.associateWith { BitSet(nUniqueCargos) }.toMutableMap()
        val outSets = graph.stations.keys.associateWith { BitSet(nUniqueCargos) }.toMutableMap()

        val worklist = BitSet(rpoStations.size)
        val s0Index = stationToRpoIndex[graph.startStation]!!

        worklist.set(s0Index)

        while (!worklist.isEmpty()) {
            val rpoIndex = worklist.nextSetBit(0)
            worklist.clear(rpoIndex)
            
            val sid = rpoStations[rpoIndex]

            val newIn = BitSet(nUniqueCargos)
            graph.predecessors[sid]?.forEach { pred -> outSets[pred]?.let {newIn.or(it)}}
            inSets[sid]!!.or(newIn)

            val newOut = transfer(graph.stations[sid]!!, inSets[sid]!!)

            if (newOut != outSets[sid]){
                outSets[sid] = newOut
                graph.successors[sid]?.forEach {
                    successor -> stationToRpoIndex[successor]?.let {worklist.set(it)}
                } 
            }
        }

        return graph.stations.keys.associateWith { sid -> bitSetToCargoSet(inSets[sid]!!) }
    }

    private fun bitSetToCargoSet(bits: BitSet): Set<Int> = buildSet {
        var i = bits.nextSetBit(0)
        while (i >= 0) {
            add(indexToCargo[i]!!)
            i = bits.nextSetBit(i + 1)
        }
    }
}
