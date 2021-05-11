package cc.appauto.lib.ng

internal fun List<HierarchyNode>.toSelectionResult(): SelectionResult {
    return SelectionResult(this)
}

class SelectionResult(val nodes: List<HierarchyNode>): Collection<HierarchyNode> by nodes {
    private fun contentCompare(s1: String?, s2: String, exactlyMatch: Boolean = false): Boolean {
        return when {
            s1 == null -> false
            exactlyMatch -> s1 == s2
            else -> s1.contains(s2)
        }
    }

    operator fun get(index: Int): HierarchyNode? {
        if (index >= nodes.lastIndex) return null
        return nodes[index]
    }

    fun className(value: String): SelectionResult {
        return filter { it.className == value }.toSelectionResult()
    }

    // return the clickable parent collection for all nodes in current selection result
    fun clickableParent(): SelectionResult {
        return filter { it.parent != null }.map { it.parent!! }.toSelectionResult()
    }

    @JvmOverloads
    fun text(value: String, exactlyMatch: Boolean = false): SelectionResult {
        return filter { contentCompare(it.text, value, exactlyMatch)}.toSelectionResult()
    }

    @JvmOverloads
    fun contentDescription(value: String, exactlyMatch: Boolean = false): SelectionResult {
        return filter {contentCompare(it.contentDescription, value, exactlyMatch)}.toSelectionResult()
    }

    fun isVisibleToUser(): SelectionResult {
        return filter {it.isVisibleToUser}.toSelectionResult()
    }

    fun selector(predicate: (hn: HierarchyNode) -> Boolean): SelectionResult {
        return filter { predicate(it)}.toSelectionResult()
    }

    override fun toString(): String {
        return nodes.joinToString("\n") { it.string }
    }
}


