package cc.appauto.lib

import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

class rhionTest {
    @Test
    fun testModifySealedObject() {
        val ctx = Context.enter()
        val scopeGlobal = ctx.initSafeStandardObjects(null, true)
        println((scopeGlobal.get("Math") as ScriptableObject).isSealed)

        // this statement will leads to exception as Math is sealed
        val obj = try {
            ctx.evaluateString(scopeGlobal, "var g1=123;Math.foo=123;g1", "cmd", 1, null)
        } catch (e: Exception) {
            println("exception occurred to change the sealed instance: ${e.message}")
            null
        }
        println("g1 is " + scopeGlobal.get("g1"))
        println("obj is " + Context.toString(obj))

        Context.exit()
    }
    @Test
    fun testJsObject() {
        val ctx = Context.enter()
        val scopeGlobal = ctx.initSafeStandardObjects(null, true)
        val s = ctx.compileString("var obj = {k1: 1, k2:'123'};obj", "<cmd>", 1, null)
        val obj = s.exec(ctx, scopeGlobal)
        println("obj is: ${Context.toObject(obj, scopeGlobal).get("k3", null)}")
        Context.exit()
    }
}