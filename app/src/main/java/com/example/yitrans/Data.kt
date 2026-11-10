package com.example.yitrans

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class Sent(val sec: String, val yi: String, val py: String, val zh: String) {
    fun toJson(): JSONObject = JSONObject().put("sec", sec).put("yi", yi).put("py", py).put("zh", zh)
    companion object {
        fun from(o: JSONObject) = Sent(o.getString("sec"), o.getString("yi"), o.getString("py"), o.getString("zh"))
    }
}

/** type: 0 彝→中选择, 1 中→彝选择, 2 判断 */
data class Q(
    val type: Int, val prompt: String, val shown: String,
    val options: List<String>, val ans: Int, val expl: String
) {
    val id: String get() = "$type|$shown|${options[ans]}"
    fun toJson(): JSONObject = JSONObject().put("type", type).put("prompt", prompt).put("shown", shown)
        .put("options", JSONArray(options)).put("ans", ans).put("expl", expl)

    companion object {
        fun from(o: JSONObject): Q {
            val a = o.getJSONArray("options")
            return Q(o.getInt("type"), o.getString("prompt"), o.getString("shown"),
                (0 until a.length()).map { a.getString(it) }, o.getInt("ans"), o.getString("expl"))
        }
    }
}

object Repo {
    var sents: List<Sent> = emptyList()
    private var cleanYi: List<String> = emptyList()
    private var cleanZh: List<String> = emptyList()
    private var roman: List<Pair<String, Char>> = emptyList()

    private fun read(c: Context, name: String) =
        c.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun load(c: Context) {
        if (sents.isNotEmpty()) return
        val a = JSONArray(read(c, "sentences.json"))
        sents = (0 until a.length()).map { Sent.from(a.getJSONObject(it)) }
        cleanYi = sents.map { clean(it.yi) }
        cleanZh = sents.map { clean(it.zh) }
        val m = JSONObject(read(c, "yimap.json"))
        roman = m.keys().asSequence().map { Pair(m.getString(it), it[0]) }
            .sortedWith(compareBy({ it.first.length }, { it.first }, { it.second })).toList()
    }

    fun clean(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()

    /** 拼音候选：先完全匹配，再前缀匹配 */
    fun cands(b: String): List<Char> {
        if (b.isEmpty()) return emptyList()
        val exact = roman.filter { it.first == b }.map { it.second }
        val pre = roman.filter { it.first != b && it.first.startsWith(b) }.map { it.second }
        return (exact + pre).distinct().take(60)
    }

    private fun grams(s: String): Set<String> =
        if (s.length < 2) s.map { it.toString() }.toSet() else (0 until s.length - 1).map { s.substring(it, it + 2) }.toSet()

    private fun score(q: String, t: String): Double {
        if (q.isEmpty() || t.isEmpty()) return 0.0
        if (t == q) return 3.0
        if (t.contains(q)) return 2.0 + q.length.toDouble() / t.length
        if (t.length >= 2 && q.contains(t)) return 1.5 + t.length.toDouble() / q.length
        val a = grams(q); val b = grams(t)
        return 2.0 * a.count { it in b } / (a.size + b.size)
    }

    /** 词句库检索（非机器翻译）：返回最相近的若干条 */
    fun search(query: String, fromYi: Boolean): List<Sent> {
        val q = clean(query)
        if (q.isEmpty()) return emptyList()
        val pool = if (fromYi) cleanYi else cleanZh
        return sents.indices.map { it to score(q, pool[it]) }
            .filter { it.second >= 0.3 }.sortedByDescending { it.second }.take(10).map { sents[it.first] }
    }

    fun makeQ(kind: Int): Q {
        val s = sents.random()
        val pool = sents.filter { it.zh != s.zh && it.yi != s.yi }.shuffled()
        val expl = "${s.yi}\n${s.py}\n${s.zh}"
        return when (kind) {
            0 -> {
                val o = (pool.distinctBy { it.zh }.take(3).map { it.zh } + s.zh).shuffled()
                Q(0, "下面的彝文是什么意思？", s.yi, o, o.indexOf(s.zh), expl)
            }
            1 -> {
                val o = (pool.distinctBy { it.yi }.take(3).map { it.yi } + s.yi).shuffled()
                Q(1, "下面的中文用彝文怎么说？", s.zh, o, o.indexOf(s.yi), expl)
            }
            else -> {
                val right = Random.nextBoolean()
                val zh = if (right) s.zh else pool.first().zh
                Q(2, "判断：下面的译文是否正确？", "${s.yi}\n译文：$zh", listOf("正确", "错误"), if (right) 0 else 1, expl)
            }
        }
    }
}
