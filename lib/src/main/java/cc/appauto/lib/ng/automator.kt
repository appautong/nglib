package cc.appauto.lib.ng

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.alibaba.fastjson.JSONObject

private val DUMMY_ACTION = fun(step: AutomationStep) {}

/** AutomationStep encapsulate the basic logic unit for automation process
 * * action: do something that make UI changes e.g.: click a button, scroll up down;
 * * postActionDelay: delay some while after action done to make sure the latest changes of UI is taken effect
 * * expect: condition that guarantee the action is done successfully
 */
class AutomationStep(val name: String, val automator: AppAutomator) {
    private var postActDelay: Long = AppAutomator.defaultPostActDelay
    private var act: ((AutomationStep) -> Unit) = DUMMY_ACTION
    private var exp: ((HierarchyTree, AutomationStep) -> Boolean)? = null

    private var retryCount: Int = AppAutomator.defaultRetryCount

    private var actionExecuted: Int = 0
    var expectedSuccess: Boolean = false
        private set

    var message: String? = null

    // delay given milli-seconds after did action to wait UI change taking place
    fun postActionDelay(ms: Long): AutomationStep {
        if (ms >= 0) postActDelay = ms
        return this
    }

    fun action(r: (AutomationStep) -> Unit): AutomationStep {
        this.act = r
        return this
    }

    fun expect(predicate: (tree: HierarchyTree, step: AutomationStep) -> Boolean): AutomationStep {
        this.exp = predicate
        return this
    }

    // retry the action n-times if expect predicate not matched
    fun retry(n: Int): AutomationStep {
        if (n >= 0) retryCount = n
        return this
    }

    private fun executeAction() {
        this.act(this)
        actionExecuted++
        if (postActDelay > 0) sleep(postActDelay)
    }

    // start the action-expect loop
    fun run(): Boolean {
        if (exp == null) {
            // set expectation result to true if no expect runnable
            expectedSuccess = true
            message = "no expectation, set step succeed"
            return expectedSuccess
        }
        do {
            // execute the action and increase the executed count
            this.executeAction()
            val tree = HierarchyTree.from(automator.srv)
            if (tree == null) {
                message = "null hierarchy tree in NO.$actionExecuted execution"
                if (actionExecuted < retryCount) {
                    continue
                } else {
                    break
                }
            }
            val matched = this.exp!!(tree, this)
            if (matched) {
                expectedSuccess = true
                message = "succeed in NO.$actionExecuted execution"
                tree.recycle()
                break
            }

            if (actionExecuted >= retryCount) {
                message = "no matched expectation after tried $actionExecuted times\ncontrols:\n${tree.hierarchyString}"
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

    val steps: MutableList<AutomationStep> = mutableListOf()

    operator fun get(key: String): Any? {
       return privateData[key]
    }
    operator fun set(key: String, value: Any) {
        privateData[key] = value
    }

    companion object {
        @JvmStatic
        var defaultRetryCount: Int = 3
        @JvmStatic
        var defaultPostActDelay: Long = 1000
    }

    fun stepOf(name: String): AutomationStep {
        val step = AutomationStep(name, this)
        steps.add(step)
        return step
    }

    fun run(): AppAutomator {
        for (step in steps) {
            if (!step.run()) {
                this.message = "run step ${step.name} failed: ${step.message}"
                return this
            }
        }
        this.message = "run ${steps.size} steps successfully"
        allStepsSucceed = true
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
            openApp(srv, srv.applicationContext, packageName)
        }.expect { tree, _ ->
            tree.packageName == packageName
        }
        steps.add(step)
        return step
    }
}