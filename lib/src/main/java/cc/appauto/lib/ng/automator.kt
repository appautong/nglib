package cc.appauto.lib.ng

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import cc.appauto.lib.tryRecycle
import com.alibaba.fastjson.JSONObject

private val DUMMY_ACTION = fun(_: AutomationStep) {}
private val DUMMY_EXPECT = fun(_: HierarchyTree, step: AutomationStep): Boolean {
    step.message = "dummy expect always returns false"
    return false
}

data class ActionTarget(val name: String, val filter: (tree: HierarchyTree) -> SelectionResult) {
    var target: AccessibilityNodeInfo? = null
        internal set

    // whether this ActionTarget is optional
    var optional: Boolean = false
}

/** AutomationStep encapsulate the basic logic unit for automation process
 * * action: do something that make UI changes e.g.: click a button, scroll up down;
 * * postActionDelay: delay some while after action done to make sure the latest changes of UI is taken effect
 * * expect: condition that guarantee the action is done successfully
 */
class AutomationStep constructor(val name: String, val automator: AppAutomator) {
    private var postActDelay: Long = AppAutomator.defaultPostActDelay
    private var preActDelay: Long = AppAutomator.defaultPreActDelay
    private var act: ((AutomationStep) -> Unit) = DUMMY_ACTION
    private var exp: ((HierarchyTree, AutomationStep) -> Boolean) = DUMMY_EXPECT

    private var retryCount: Int = AppAutomator.defaultRetryCount

    private var actionExecuted: Int = 0
    var expectedSuccess: Boolean = false
        private set

    var message: String? = null

    private var actTargets: MutableMap<String, ActionTarget> = mutableMapOf()

    // setupActionNode add a constraint on given action node
    // all action nodes shall be ready before the action executed
    fun setupActionNode(name: String, filter: (tree: HierarchyTree) -> SelectionResult): AutomationStep    {
        val elem = ActionTarget(name, filter)
        actTargets.put(name, elem)
        return this
    }

    // setupActionNode add a constraint on given action node
    // later, actionTargetIsFound can be used to test whether given action node is found
    fun setupOptionalActionNode(name: String, filter: (tree: HierarchyTree) -> SelectionResult): AutomationStep    {
        val elem = ActionTarget(name, filter)
        elem.optional = true
        actTargets.put(name, elem)
        return this
    }

    fun actionTargetIsFound(name: String): Boolean {
        return actTargets[name]?.target != null
    }

    // getActionNodeInfo is a helper function to be called in action callback
    // the node info object will be recycled automatically when automator finished running
    fun getActionNodeInfo(name: String): AccessibilityNodeInfo {
        if (!actTargets.containsKey(name)) {
           throw IndexOutOfBoundsException("no key: $name in action targets map")
        }
        return actTargets[name]!!.target!!
    }

    internal fun recycleActionNodes() {
        actTargets.forEach { (_, t ) ->
            t.target?.tryRecycle()
        }
        actTargets.clear()
    }

    // delay given milli-seconds after did action to wait UI change taking place
    fun postActionDelay(ms: Long): AutomationStep {
        if (ms >= 0) postActDelay = ms
        return this
    }

    // delay given milli-seconds if there are action targets for current step
    fun preActionDelay(ms: Long): AutomationStep {
        if (ms >= 0) preActDelay = ms
        return this
    }


    // if not specified a action handler, use a dummy action handler do nothing
    fun action(r: (AutomationStep) -> Unit): AutomationStep {
        this.act = r
        return this
    }

    // if not specified a predicate, use a dummy expect handler return false always
    fun expect(predicate: (tree: HierarchyTree, step: AutomationStep) -> Boolean): AutomationStep {
        this.exp = predicate
        return this
    }

    // retry the action n-times if expect predicate not matched
    fun retry(n: Int): AutomationStep {
        if (n >= 0) retryCount = n
        return this
    }

    // return whether all action targets are found
    private fun prepareAllActionTarget(): Boolean {
        if (actTargets.isEmpty()) return true
        sleep(preActDelay)

        val tree = HierarchyTree.from(automator.srv)
        if (tree == null) {
            this.message = "prepareAllActionTarget: create hierarchy tree from automator.srv failed"
            return false
        }
        for ((_, t) in actTargets) {
            // recycle the previous target if existed
            t.target?.tryRecycle()
            t.target = null

            val res = t.filter(tree)
            if (res.isEmpty() && !t.optional) {
                this.message = "can not find mandatory action node ${t.name}"
                tree.recycle()
                return false
            }

            if (res.isEmpty()) continue

            res.first().also {
                Log.d(TAG, "found action target: ${t.name}, optional: ${t.optional}, ${it.string}")
                t.target = tree.getAccessibilityNodeInfo(it)
                tree.markKept(it)
            }
        }
        tree.recycle()
        return true
    }

    // start the action-expect loop
    fun run(): Boolean {
        do {
            actionExecuted++

            Log.v(TAG, "${automator.name}: run step $name for $actionExecuted times")
            if (!this.prepareAllActionTarget()) {
                if (actionExecuted > retryCount) break else continue
            }
            this.act(this)
            sleep(postActDelay)

            val tree = HierarchyTree.from(automator.srv)
            if (tree == null) {
                message = "null hierarchy tree in NO.$actionExecuted execution"
                if (actionExecuted > retryCount) break else continue
            }

            val matched = this.exp(tree, this)
            if (matched) {
                expectedSuccess = true
                message = "succeed in NO.$actionExecuted execution"
                tree.recycle()
                break
            }

            if (actionExecuted >= retryCount) {
                message = "no matched expectation after tried $actionExecuted times"
                tree.recycle()
                break
            }
            tree.recycle()
        } while(true)
        return expectedSuccess
    }
}

class AppAutomator(val srv: AccessibilityService, val name: String) {
    private val privateData = JSONObject()

    var allStepsSucceed = false
    var message: String? = null
        private set

    var failedHierarchyString: String? = null
        private set

    val steps: MutableList<AutomationStep> = mutableListOf()

    operator fun get(key: String): Any? {
       return privateData[key]
    }
    operator fun set(key: String, value: Any) {
        privateData[key] = value
    }

    companion object {
        @JvmStatic
        var defaultRetryCount: Int = 2
        @JvmStatic
        var defaultPostActDelay: Long = 2000
        @JvmStatic
        var defaultPreActDelay: Long = 0
    }

    fun stepOf(name: String): AutomationStep {
        val step = AutomationStep(name, this)
        steps.add(step)
        return step
    }

    fun run(): AppAutomator {
        var anyStepFail = false
        for (step in steps) {
            if (!step.run()) {
                this.message = "run step ${step.name} failed: ${step.message}"
                anyStepFail = true
                failedHierarchyString = srv.getHierarchyString()
                break
            }
        }
        if (!anyStepFail) {
            this.message = "run ${steps.size} steps successfully"
            allStepsSucceed = true
        }

        // recycle all node info accuquired in each step
        steps.forEach {
            it.recycleActionNodes()
        }
        return this
    }

    fun close() {
        privateData.clear()
    }

    protected fun finalize() {
        close()
    }

    fun stepOfOpeningApp(packageName: String): AutomationStep {
        val step = AutomationStep("step_open_app_$packageName", this)
        step.retry(0).postActionDelay(0).action {
            quitApp(srv, packageName)
            openApp(srv, packageName)
        }.expect { tree, _ ->
            tree.packageName == packageName
        }
        steps.add(step)
        return step
    }
}