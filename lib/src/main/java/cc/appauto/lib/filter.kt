package cc.appauto.lib

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo


enum class FilterResult {
    /**
     * Abort: current node is not matched, and abort the filter process the node's descendant
     * Continue: current node is not matched, could continue the filter process on its descendant
     * ContinueKept: the same as continue, but current node is kept by current filter, the traverse process will not free/recycle it
     * Match: current node is matched by the filter
     */
    Abort, Continue, ContinueKept, Match
}

// typealias Filter  = (AccessibilityNodeInfo) -> FilterResult

interface Filter {
    fun filter(node: AccessibilityNodeInfo) : FilterResult
}

class NamedFilter(val name: String, val f: Filter): Filter {
    override fun filter(node: AccessibilityNodeInfo): FilterResult {
        return f.filter(node)
    }

}

class MultipleNodeFilter(vararg filters: NamedFilter) : Filter {
    private val activeFilters: MutableList<NamedFilter> = mutableListOf(*filters)
    private val matchedFilters: MutableList<NamedFilter> = mutableListOf()
    val result = mutableMapOf<String, AccessibilityNodeInfo>()

    override fun filter(node: AccessibilityNodeInfo) : FilterResult {
        if(traceFilter) Log.v(TAG, "filters size: ${activeFilters.size}")
        var matched = false
        for (f in activeFilters) {
            val res = f.filter(node)
            if (traceFilter) Log.v(TAG, "checking ${node.string()}, res: ${res.name}")
            if (res == FilterResult.Match) {
                if (traceFilter) Log.v(TAG, "${node.string()} matched, removed from active filter")
                matchedFilters.add(f)
                matched = true
                result[f.name] = node
            } else if (res == FilterResult.Abort) {
                return res
            }
        }
        if (matched) {
            matchedFilters.forEach { activeFilters.remove(it) }
            matchedFilters.clear()
        }
        if (activeFilters.size == 0) return FilterResult.Match
        if (matched) {
            return FilterResult.ContinueKept
        }
        return FilterResult.Continue
    }

    fun clear() {
        matchedFilters.clear()
        activeFilters.clear()
        result.clear()
    }

    fun recycleAllResult() {
        result.forEach { _, v -> v.tryRecycle() }
    }
}

fun classFilter(node: AccessibilityNodeInfo, className: String) : FilterResult {
    if (node.className == className) return FilterResult.Match
    if (traceFilter) Log.w(TAG, "classFilter not match: ${node.className} <> $className")
    return FilterResult.Continue
}

fun textFilter(node: AccessibilityNodeInfo, vararg texts: String): FilterResult {
    if (texts.isNullOrEmpty()) {
        if (node.text.isNullOrEmpty()) {
            return FilterResult.Match
        }
        if (traceFilter) Log.w(TAG, "textFilter not match: passed texts is null/empty while node.text not")
        return FilterResult.Continue
    }
    if (node.text.isNullOrEmpty()) {
        if (traceFilter)  Log.w(TAG, "textFilter not match: passed texts is ${texts.joinToString(",")} while node.text is null/empty")
        return FilterResult.Continue
    }
    if (texts.any {node.text.contains(it, true)}) return FilterResult.Match

    if (traceFilter)  Log.w(TAG, "textFilter not match: passed texts is ${texts.joinToString(",")} <> ${node.text}")
    return FilterResult.Continue
}

fun viewIDFilter(node: AccessibilityNodeInfo, vararg texts: String): FilterResult {
    val id = node.viewID()
    if (id == null) return FilterResult.Continue
    if (texts.any {id.contains(it, true)}) return FilterResult.Match
    return FilterResult.Continue
}

fun classIDFilter(node: AccessibilityNodeInfo, className: String, vararg ids: String): FilterResult {
    if (classFilter(node, className) == FilterResult.Match) {
        return viewIDFilter(node, *ids)
    }
    return FilterResult.Continue
}


fun classTextFilter(node: AccessibilityNodeInfo, className: String, vararg texts: String): FilterResult {
    if (classFilter(node, className) == FilterResult.Match) {
        return textFilter(node, *texts)
    }
    return FilterResult.Continue
}

fun classTextParentClassFilter(node: AccessibilityNodeInfo, className: String, parentClassName: String, vararg texts: String): FilterResult {
    val parent = try {
        node.parent
    } catch (e: Exception) {
        Log.w(TAG, "classTextParentClassFilter: parent leads to exception: ${Log.getStackTraceString(e)}")
        null
    } ?: return FilterResult.Continue

    val cls = parent.className
    parent.recycle()

    if (cls != parentClassName) {
        if (traceFilter) Log.w(TAG, "parent class not match: $cls <> $parentClassName")
        return FilterResult.Continue
    }

    return classTextFilter(node, className, *texts)
}

fun contentDescriptionFilter(node: AccessibilityNodeInfo, vararg texts: String): FilterResult {
    if (texts.isNullOrEmpty()) return FilterResult.Continue
    if (node.contentDescription.isNullOrEmpty()) return FilterResult.Continue
    if (texts.any {node.contentDescription.contains(it, true)}) return FilterResult.Match

    return FilterResult.Continue
}

fun classContentDescriptionFilter(node: AccessibilityNodeInfo, className: String, vararg texts: String): FilterResult {
    if (classFilter(node, className) == FilterResult.Match) {
        return contentDescriptionFilter(node, *texts)
    }
    return FilterResult.Continue
}

// position filter return Abort if not matched
fun positionFilter(node: AccessibilityNodeInfo, parent: Rect, vararg positions: Position): FilterResult {
    val bound = node.bound()

    var res: Boolean
    for (pos in positions) {
        res = when(pos) {
            Position.Top -> bound.inTop(parent)
            Position.Bottom -> bound.inBottom(parent)
            Position.VMiddle -> bound.inVMiddle(parent)
            Position.Left -> bound.inLeft(parent)
            Position.HMiddle -> bound.inHMiddle(parent)
            Position.Right -> bound.inRight(parent)
        }
        if (!res) {
            return FilterResult.Abort
        }
    }
    return FilterResult.Match
}

fun newFilter(f: (AccessibilityNodeInfo) -> FilterResult): Filter {
    return object: Filter {
        override fun filter(node: AccessibilityNodeInfo): FilterResult {
            return f(node)
        }
    }
}

fun newNamedFilter(name: String, f: (AccessibilityNodeInfo) -> FilterResult): NamedFilter {
    return NamedFilter(name, newFilter(f))
}


fun newBoolFilter(f: (AccessibilityNodeInfo) -> Boolean): Filter {
    return object: Filter {
        override fun filter(node: AccessibilityNodeInfo): FilterResult {
            if (f(node)) return FilterResult.Match
            return FilterResult.Continue
        }
    }
}
