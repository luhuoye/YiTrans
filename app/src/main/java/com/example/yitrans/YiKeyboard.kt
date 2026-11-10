package com.example.yitrans

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 内置凉山彝文键盘：拼音(彝文拉丁转写)候选 + 完整彝文字表(U+A000–A48C) */
@Composable
fun YiKeyboard(onText: (String) -> Unit, onBack: () -> Unit) {
    var buf by remember { mutableStateOf("") }
    var table by remember { mutableStateOf(false) }
    val cands = remember(buf) { Repo.cands(buf) }

    Column(
        Modifier.fillMaxWidth().background(Color(0xFFE3E6EA)).padding(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(46.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (buf.isEmpty()) "输入拼音如 ne，或点「字表」" else buf,
                fontSize = 13.sp, color = Color.DarkGray, modifier = Modifier.padding(horizontal = 6.dp)
            )
            cands.forEach { c ->
                Text(
                    c.toString(), fontSize = 26.sp,
                    modifier = Modifier.clickable { onText(c.toString()); buf = "" }.padding(horizontal = 10.dp)
                )
            }
        }
        if (table) {
            LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxWidth().height(216.dp)) {
                items((0xA000..0xA48C).toList()) { cp ->
                    Text(
                        cp.toChar().toString(), fontSize = 24.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { onText(cp.toChar().toString()) }
                            .padding(vertical = 6.dp)
                    )
                }
            }
        } else {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).padding(horizontal = if (row.length < 10) 14.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    row.forEach { ch -> Key(ch.toString(), Modifier.weight(1f)) { buf += ch } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Key(if (table) "ABC" else "字表", Modifier.weight(1.4f)) { table = !table }
            Key("，", Modifier.weight(1f)) { onText("，") }
            Key("。", Modifier.weight(1f)) { onText("。") }
            Key("？", Modifier.weight(1f)) { onText("？") }
            Key("！", Modifier.weight(1f)) { onText("！") }
            Key("空格", Modifier.weight(2f)) {
                if (buf.isNotEmpty()) { cands.firstOrNull()?.let { onText(it.toString()) }; buf = "" } else onText(" ")
            }
            Key("⌫", Modifier.weight(1.4f)) { if (buf.isNotEmpty()) buf = buf.dropLast(1) else onBack() }
        }
    }
}

@Composable
private fun Key(t: String, m: Modifier, onClick: () -> Unit) {
    Box(
        m.height(44.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(t, fontSize = 16.sp) }
}
