package cc.appauto.lib

import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.IdScriptableObject
import org.mozilla.javascript.ScriptableObject

class rhionTest {
    @Test
    fun testModifySealedObject() {
        val ctx = Context.enter()
        val scopeGlobal = ctx.initSafeStandardObjects(null, true)
        println((scopeGlobal.get("Math") as ScriptableObject).isSealed)
        val obj = ctx.evaluateString(scopeGlobal, "var g1=123;Math.foo=123;g1", "cmd", 1, null)
        println("g1 is " + scopeGlobal.get("g1"))
        println("obj is " + Context.toString(obj))

        Context.exit()
    }
}