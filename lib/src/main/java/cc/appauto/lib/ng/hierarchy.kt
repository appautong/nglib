package cc.appauto.lib.ng

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class HierarchyTree private constructor() {
    var windowId: Int = -1
        private set

    var packageName: String? = null
        private set

    var root: HierarchyNode? = null
        private set

    private var accessibilityNodes: MutableMap<String, AccessibilityNodeInfo> = mutableMapOf()
    private val accessibilityNodesKept: MutableSet<String> = mutableSetOf()

    // construct the hierarchy from given AccessibilityNodeInfo
    private fun setupHierarchy(node: AccessibilityNodeInfo, parent: HierarchyNode?): HierarchyNode {
        val hn = HierarchyNode(System.identityHashCode(node).toString())
        accessibilityNodes.put(hn.id, node)

        if (parent != null) {
            hn.parent = parent
            hn.depth = parent.depth+1
        } else {
            hn.depth = 0
        }

        hn.windowId = node.windowId
        hn.viewId = node.viewID()
        hn.className = node.className.toString()

        if(node.text != null) hn.text = node.text.toString()
        if(node.contentDescription != null) hn.contentDescription = node.contentDescription.toString()

        hn.bound = node.bound()

        hn.isVisibleToUser = node.isVisibleToUser
        hn.isClickable = node.isClickable
        hn.isScrollable = node.isScrollable
        hn.isEditable = node.isEditable

        // setup hierarchy information
        for (idx in 0 until node.childCount) {
            val nodeChild = node.tryGetChild(idx)
            var hnChild: HierarchyNode?
            if (nodeChild != null) {
                hnChild = setupHierarchy(nodeChild, hn)
                hn.children.add(hnChild)
            } else {
                hnChild = HierarchyNode("dummy")
            }
            hnChild.siblings = hn.children
            hnChild.siblingIndex = idx
        }

        return hn
    }

    companion object {
        // do not recycle the root node, HierarchyTree.recycle would recycle it
        fun from(root: AccessibilityNodeInfo): HierarchyTree {
            val tree = HierarchyTree()
            tree.packageName = root.packageName.toString()
            tree.windowId = root.windowId
            tree.root = tree.setupHierarchy(root, null)
            return tree
        }

        fun from(srv: AccessibilityService, packageName: String? = null): HierarchyTree? {
            val root = getTopAppNode(srv, packageName) ?: return null
            return from(root)
        }
    }

    protected fun finalize() {
        Log.v(TAG, "finalize called for: ${this.windowId}")
        recycle()
    }

    // no operation shall be done with this tree object afeter recycle
    fun recycle() {
        Log.v(TAG, "recycle the hierarchy: ${this.windowId}")
        this.root = null

        val tmp = this.accessibilityNodes
        this.accessibilityNodes = mutableMapOf()

        tmp.forEach {
            try {
                if (!accessibilityNodesKept.contains(it.key)) it.value.recycle()
            } catch (e: Exception) {
                Log.w(TAG, "recycle node ${it.key} leads to exception: ${e.message}")
            }
        }
        tmp.clear()
        accessibilityNodesKept.clear()
    }

    fun filter(predicate: (HierarchyNode)->Boolean): List<HierarchyNode> {
        if (this.root == null) return listOf()
        return this.root!!.filter(predicate)
    }

    fun print() = this.root?.print()

    val hierarchyString
        get() = if (root == null)
            "null root node"
        else {
            val sb = StringBuilder()
            root!!.walk { sb.appendLine(it.string()) }
            sb.toString()
        }

    // mark the corresponded AccessibilityNodeInfo shall not be recycled by HierarchyTree.recycle
    fun markKept(vararg nodes: HierarchyNode) {
        nodes.forEach { accessibilityNodesKept.add(it.id) }
    }

    // AccessibilityNodeInfo shall be not recycled manually, invoke HierarchyTree.recycle instead
    fun getAccessibilityNodeInfo(node: HierarchyNode): AccessibilityNodeInfo? {
        return accessibilityNodes.get(node.id)
    }
}

class HierarchyNode(val id: String) {
    // depth level in the hierarchy tree, 0 for node without parent
    var depth: Int = -1

    var parent: HierarchyNode? = null
    var children: MutableList<HierarchyNode> =  mutableListOf()

    // siblings equals to parent.children
    var siblings: MutableList<HierarchyNode> = mutableListOf()

    // this node's index in the sibling list; -1 for node without parent
    var siblingIndex: Int = -1

    var windowId: Int = -1
    var viewId: String? = null
    var className: String = ""
    var text: String? = null
    var contentDescription: String? = null
    var bound: Rect? = null

    var isVisibleToUser = false
    var isClickable = false
    var isScrollable = false
    var isEditable = false

    val isLastInSibling: Boolean
        get() {
            return siblingIndex >= 0 && siblingIndex+1 == siblings.size
        }

    /** return ancestor of the current node
     *  n:  0 return current; 1 parent; 2 parent of parent; etc...
     */
    fun ancestor(n: Int): HierarchyNode? {
        if (n > depth) return null
        if (n == 0) return this

        var node: HierarchyNode = this
        for (i in n downTo 1) {
            node = node.parent!!
        }
        return node
    }

    /** return sibling of current node
     * n: 0 return current; 1 next sibling; -1 previous sibling
     */
    fun sibling(n: Int): HierarchyNode? {
        if (n == 0) return this
        val idx = n+this.siblingIndex
        if (this.siblings.indices.contains(idx)) return this.siblings[idx]
        return null
    }

    fun string(indent: Boolean=false):String {
        val sb = StringBuilder()
        if (indent) {
            if (depth > 0) {
                // if ancestor(i) is not the last element, '|' shall be drawn
                for (i in this.depth-1 downTo 1) {
                    val tmp = ancestor(i)!!
                    if (!tmp.isLastInSibling) sb.append("|   ") else sb.append("    ")
                }

                // draw lines to current node
                sb.append("|___")
            }
        }
        sb.append("$siblingIndex $viewId/${parent?.viewId} $className $text/$contentDescription v/c/e/s:$isVisibleToUser/$isClickable/$isEditable/$isScrollable ${bound?.toShortString()} ${children.size} w:$windowId id: $id")
        return sb.toString()
    }

    fun print() = walk {  Log.i(TAG, it.string(true))}

    // walk down the hierarchy to leaf node and call action each element (include the current node)
    fun walk(action: (HierarchyNode) -> Unit) {
        action(this)
        this.children.forEach {
            it.walk(action)
        }
    }

    // walk up the hierarchy to root(top) node and call predicate on each element (include the current node)
    fun walkAncestors(action: (HierarchyNode) -> Unit) {
        action(this)
        var node = this.parent
        while(node != null) {
            action(node)
            node = node.parent
        }
    }

    fun findAncestor(predicate: (HierarchyNode) -> Boolean, n: Int = -1): List<HierarchyNode> {
        var found = 0
        val ret: MutableList<HierarchyNode> = mutableListOf()
        var node:HierarchyNode = this
        do {
            if (predicate(node)) {
                ret.add(node)
                found++
                if (n > 0 && found == n) break
            }
            node = node.parent ?: break
        } while(true)
        return ret
    }

    // walk down the hierarchy to leaf node and call predicate on each element (include the current node)
    fun filter(predicate: (HierarchyNode)->Boolean): List<HierarchyNode> {
        val ret: MutableList<HierarchyNode> = mutableListOf()
        this.walk {
            if (predicate(it)) ret.add(it)
        }
        return ret
    }

    // walk up the hierarchy to root(top) node and call predicate on each element (include the current node)
    fun filterAncestors(predicate: (HierarchyNode)->Boolean): List<HierarchyNode> {
        val ret: MutableList<HierarchyNode> = mutableListOf()
        this.walkAncestors {
            if (predicate(it)) ret.add(it)
        }
        return ret
    }

    fun getClassHierarchyUpToRoot(): List<String>  {
       val ret: MutableList<String> = mutableListOf()
        this.walkAncestors { ret.add(0, it.className) }
        return ret
    }

    fun getClassHierarchyDownToLeaf(): List<String>  {
        val ret: MutableList<String> = mutableListOf()
        this.walk { ret.add(0, it.className) }
        return ret
    }

    val clickableAncestor: HierarchyNode?
        get() {
            return findAncestor({ it.isClickable }).firstOrNull()
        }
}