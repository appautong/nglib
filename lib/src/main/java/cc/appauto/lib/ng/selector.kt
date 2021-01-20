package cc.appauto.lib.ng

/** return all nodes match the className selectors as following:
 *  descendant: A B
 *  child: A > B
 */
fun HierarchyTree.className(selector: String): List<HierarchyNode> {
    if (selector.isBlank()) return listOf()

    var ret: List<HierarchyNode>?
    // convert selector string to parsed token list. e.g:
    // "A  >  B  C >  D E >  F > G" =>
    // [[A], [B, C], [D, E], [F], [G]]

    // pcList: parent child relationship list
    val pcList = mutableListOf<List<String>>()
    val tmp = selector.split(">")
    tmp.forEach {it.trim()}

    var minDepth = -1
    for (i in tmp.indices) {
        var fields = tmp[i].split(" ")
        fields = fields.filter { it.isNotBlank() }
        pcList.add(fields)
        minDepth += fields.size
    }

    // find all elements with given classname and depth >= minDepth
    val cls = pcList.last().last()
    ret = this.filter {  it.depth >= minDepth && it.className == cls }

    // construct the regexp string to match class hierarchy string
    val regexpList: MutableList<String> = mutableListOf()
    for (elem in pcList) {
        regexpList.add(elem.joinToString(".*"))
    }
    val selectorRegexp = Regex(regexpList.joinToString(">"))

    return ret.filter {
        selectorRegexp.containsMatchIn(it.getClassHierarchyUpToRoot().joinToString(">"))
    }
}

fun List<HierarchyNode>.className(value: String): List<HierarchyNode> {
    return this.filter { it.className == value }
}

private fun contentCompare(s1: String?, s2: String, exactlyMatch: Boolean = false): Boolean {
    return when {
        s1 == null -> false
        exactlyMatch -> s1 == s2
        else -> s1.contains(s2)
    }
}

fun List<HierarchyNode>.text(value: String, exactlyMatch: Boolean = false): List<HierarchyNode> {
    return this.filter { contentCompare(it.text, value, exactlyMatch) }
}

fun List<HierarchyNode>.contentDescription(value: String, exactlyMatch: Boolean = false): List<HierarchyNode> {
    return this.filter { contentCompare(it.contentDescription, value, exactlyMatch) }
}

fun List<HierarchyNode>.isVisibleToUser(): List<HierarchyNode> {
    return this.filter { it.isVisibleToUser }
}
