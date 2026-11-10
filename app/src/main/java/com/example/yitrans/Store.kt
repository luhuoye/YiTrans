package com.example.yitrans

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("yi_fav", Context.MODE_PRIVATE)
    val favS = mutableStateListOf<Sent>()
    val favQ = mutableStateListOf<Q>()

    init {
        runCatching {
            val a = JSONArray(sp.getString("s", "[]"))
            for (i in 0 until a.length()) favS.add(Sent.from(a.getJSONObject(i)))
            val b = JSONArray(sp.getString("q", "[]"))
            for (i in 0 until b.length()) favQ.add(Q.from(b.getJSONObject(i)))
        }
    }

    private fun save() {
        sp.edit()
            .putString("s", JSONArray(favS.map { it.toJson() }).toString())
            .putString("q", JSONArray(favQ.map { it.toJson() }).toString())
            .apply()
    }

    fun toggleS(s: Sent) {
        val i = favS.indexOfFirst { it.yi == s.yi && it.zh == s.zh }
        if (i >= 0) favS.removeAt(i) else favS.add(0, s)
        save()
    }

    fun toggleQ(q: Q) {
        val i = favQ.indexOfFirst { it.id == q.id }
        if (i >= 0) favQ.removeAt(i) else favQ.add(0, q)
        save()
    }
}
