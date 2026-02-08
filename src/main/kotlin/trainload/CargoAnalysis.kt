package trainload

import java.util.BitSet
import java.util.LinkedList

class CargoAnalysis(private val graph: RailwayGraph) {

    private val cargoToIndex: Map<Int, Int>
    private val indexToCargo: Map<Int, Int>
    private val nUniqueCargos: Int

    init {
        val sorted = graph.stations.values
            .flatMap { listOf(it.unload, it.load) }
            .distinct()
            .sorted()
        cargoToIndex = sorted.withIndex().associate { (i, c) -> c to i }
        indexToCargo = cargoToIndex.entries.associate { (k, v) -> v to k }
        nUniqueCargos = sorted.size
    }

    // OUT(s) = (IN(s) \ {unload(s)}) ∪ {load(s)}
    private fun transfer(station: Station, inSet: BitSet): BitSet {
        val out = inSet.clone() as BitSet
        cargoToIndex[station.unload]?.let { out.clear(it) }
        cargoToIndex[station.load]?.let { out.set(it) }
        return out
    }

    fun analyze(): Map<Int, Set<Int>> {
        val inSets  = graph.stations.keys.associateWith { BitSet(nUniqueCargos) }.toMutableMap()
        val outSets = graph.stations.keys.associateWith { BitSet(nUniqueCargos) }.toMutableMap()

        val s0 = graph.startStation
        outSets[s0] = transfer(graph.stations[s0]!!, inSets[s0]!!)

        val worklist = LinkedList<Int>()
        val inWorklist = mutableSetOf<Int>()
        for (successor in graph.successors[s0].orEmpty()) {
            if (inWorklist.add(successor)) worklist.add(successor)
        }


        while (worklist.isNotEmpty()) {
            val sid = worklist.poll()
            inWorklist.remove(sid)


            val newIn = BitSet(nUniqueCargos)
            for (predecessor in graph.predecessors[sid].orEmpty()) {
                newIn.or(outSets[predecessor]!!)
            }

            inSets[sid] = newIn

            val newOut = transfer(graph.stations[sid]!!, newIn)

            if (newOut != outSets[sid]) {
                outSets[sid] = newOut
                for (successor in graph.successors[sid].orEmpty()) {
                    if (inWorklist.add(successor)) worklist.add(successor)
                }
            }
        }

        return graph.stations.keys.associateWith { sid ->
            bitSetToCargoSet(inSets[sid]!!)
        }
    }

    private fun bitSetToCargoSet(bits: BitSet): Set<Int> = buildSet {
        var i = bits.nextSetBit(0)
        while (i >= 0) {
            add(indexToCargo[i]!!)
            i = bits.nextSetBit(i + 1)
        }
    }
}
