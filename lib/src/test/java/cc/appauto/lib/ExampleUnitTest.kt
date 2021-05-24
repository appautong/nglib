package cc.appauto.lib

import cc.appauto.lib.ng.ClassName
import com.alibaba.fastjson.JSON
import org.junit.Test
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        print(1.2345f.toInt())
    }

    fun foo(min: Long, max: Long = min) {
        println("min: $min, max: $max")
    }

    @Test
    fun testDateString() {
        var c = GregorianCalendar(Locale.CHINA)
        c.add(Calendar.MONTH, -1)
        c.add(Calendar.DATE, -28)
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        println(df.format(c.time))

        val df2 = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
        println(df2.format(c.time))
    }

    @Test
    fun testRegexp() {
        val v = "2018-2-15 江米给大家拜年啦"
        val v2 = "30天前 减肥想吃炸鸡的时候"
        val v3 = "5个月前 百因必有果 下个"
        val r = Regex("""(\d{4}-\d{1,2}-\d{1,2})\s+(.*)""")
        val r2 = Regex("""(\d{1,2})天前\s+(.*)""")
        val r3 = Regex("""(\d{1,2})个月前\s+(.*)""")
        val res = r.matchEntire(v)
        val res2 = r2.matchEntire(v2)
        val res3 = r3.matchEntire(v3)
        if (res?.groupValues != null) println(res.groupValues.joinToString(","))
        if (res2?.groupValues != null) println(res2.groupValues.joinToString(","))
        if (res3?.groupValues != null) println(res3.groupValues.joinToString(","))
        println(-res2!!.groupValues[1].toInt())
    }

    @Test
    fun testReplaceUsingRegexp() {
        val template = "\${time} \${name}您好，点击链接查看健康报告：https://w.url.cn/s/A7s5EU4"
        val r = Regex("""\$\{(\w+)\}""")
        val ret = r.replace(template) {
            when(it.groupValues[1]) {
                "time1" -> "2020-01-02 11:22:33"
                "name1" -> "testname"
                else -> ""
            }
        }
        println("template is $template, ret is: $ret")
    }

    @Test
    fun testRange() {
      for (i in 1 downTo  1) {
        println(i)
      }
    }

    @Test
    fun testFilter() {
        val arr = listOf(1, 2, 3, 4)
        println(arr.filter { it > 5 }.map { it * it })
    }

    @Test
    fun testSelectorParse() {
        val selector = "A  >  B  C >  D E >  F > G"
        val tmp = selector.split(">")
        val pcList = mutableListOf<List<String>>()
        tmp.forEach {it.trim()}
        for (i in tmp.indices) {
            var fields = tmp[i].split(" ")
            fields = fields.filter { it.isNotBlank() }
            pcList.add(fields)
        }
        val regexpList: MutableList<String> = mutableListOf()
        for (elem in pcList) {
            regexpList.add(elem.joinToString(".*"))
        }
        val selectorRegexp = Regex(regexpList.joinToString(">"))
        println(selectorRegexp.toString())
        println(selectorRegexp.containsMatchIn("Z>A>B>M>N>L>C>D>M>E>F>G>O>P"))
    }

    @Test
    fun testEnum() {
        println("${ClassName.ViewPager}")
    }
}
