@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.yitrans

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.load(this)
        val store = Store(this)
        setContent { MaterialTheme { App(store) } }
    }
}

@Composable
fun App(st: Store) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var fromYi by rememberSaveable { mutableStateOf(true) }
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("翻译", "做题", "收藏").forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = tab == i, onClick = { tab = i },
                    icon = { Text(listOf("译", "题", "★")[i], fontSize = 18.sp) }, label = { Text(t) }
                )
            }
        }
    }) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            when (tab) {
                0 -> TranslateScreen(st, query, { query = it }, fromYi, { fromYi = it; query = "" })
                1 -> QuizScreen(st)
                else -> FavScreen(st)
            }
        }
    }
}

@Composable
fun TranslateScreen(st: Store, query: String, onQuery: (String) -> Unit, fromYi: Boolean, onDir: (Boolean) -> Unit) {
    var dialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = fromYi, onClick = { onDir(true) }, label = { Text("彝文→中文") })
            FilterChip(selected = !fromYi, onClick = { onDir(false) }, label = { Text("中文→彝文") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onQuery("") }) { Text("清空") }
        }
        if (fromYi) {
            // 彝文模式：不放系统输入框，系统键盘不会弹出，底部直接显示内置彝文键盘
            val kc = LocalSoftwareKeyboardController.current
            val fm = LocalFocusManager.current
            LaunchedEffect(Unit) { kc?.hide(); fm.clearFocus() }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp)
                    .background(Color(0xFFF1F1F1), RoundedCornerShape(8.dp)).padding(12.dp)
            ) {
                Text(
                    if (query.isEmpty()) "用下方彝文键盘输入…" else "$query|", fontSize = 24.sp,
                    color = if (query.isEmpty()) Color.Gray else Color.Black
                )
            }
        } else {
            val fr = remember { FocusRequester() }
            OutlinedTextField(
                value = query, onValueChange = onQuery, placeholder = { Text("输入中文词或句") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).focusRequester(fr)
            )
            LaunchedEffect(Unit) { fr.requestFocus() }
        }
        val res = remember(query, fromYi) { Repo.search(query, fromYi) }
        if (query.isNotBlank()) {
            OutlinedButton(onClick = { dialog = true }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("＋ 收藏我输入的这个词/句")
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            if (query.isNotBlank() && res.isEmpty()) item { Text("词句库里没有相近的内容", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
            items(res) { SentCard(it, st) }
        }
        if (fromYi) YiKeyboard(onText = { onQuery(query + it) }, onBack = { onQuery(query.dropLast(1)) })
    }
    if (dialog) {
        var other by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("收藏词/句") },
            text = {
                Column {
                    Text(query, fontSize = 20.sp)
                    OutlinedTextField(other, { other = it }, label = { Text(if (fromYi) "中文意思" else "对应彝文(可粘贴)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (other.isNotBlank()) {
                        st.toggleS(if (fromYi) Sent("自定义", query, "", other) else Sent("自定义", other, "", query))
                    }
                    dialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("取消") } }
        )
    }
}

@Composable
fun SentCard(s: Sent, st: Store) {
    val fav = st.favS.any { it.yi == s.yi && it.zh == s.zh }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(s.yi, fontSize = 22.sp)
                if (s.py.isNotEmpty()) Text(s.py, fontSize = 13.sp, color = Color.Gray)
                Text(s.zh, fontSize = 17.sp)
                Text(s.sec, fontSize = 11.sp, color = Color.Gray)
            }
            TextButton(onClick = { st.toggleS(s) }) { Text(if (fav) "★" else "☆", fontSize = 26.sp) }
        }
    }
}

@Composable
fun QuizScreen(st: Store) {
    var mode by rememberSaveable { mutableIntStateOf(3) } // 0彝→中 1中→彝 2判断 3混合 4收藏题
    var q by remember { mutableStateOf<Q?>(null) }
    var pick by remember { mutableIntStateOf(-1) }
    var right by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    fun next() {
        pick = -1
        q = if (mode == 4) st.favQ.randomOrNull() else Repo.makeQ(if (mode == 3) (0..2).random() else mode)
    }
    LaunchedEffect(mode) { next() }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("混合", "彝→中", "中→彝", "判断", "收藏题").let { names ->
                val modes = listOf(3, 0, 1, 2, 4)
                names.forEachIndexed { i, n -> FilterChip(selected = mode == modes[i], onClick = { mode = modes[i] }, label = { Text(n) }) }
            }
        }
        Text("答对 $right / $total", color = Color.Gray)
        val cur = q
        if (cur == null) {
            Text(if (mode == 4) "还没有收藏的题目" else "加载中…", color = Color.Gray)
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(cur.prompt, fontSize = 15.sp, color = Color.Gray)
                Text(cur.shown, fontSize = 24.sp)
                cur.options.forEachIndexed { i, o ->
                    val bg = when {
                        pick < 0 -> Color(0xFFE8EAF6)
                        i == cur.ans -> Color(0xFFC8E6C9)
                        i == pick -> Color(0xFFFFCDD2)
                        else -> Color(0xFFF5F5F5)
                    }
                    Button(
                        onClick = { if (pick < 0) { pick = i; total++; if (i == cur.ans) right++ } },
                        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                    ) { Text(o, fontSize = if (cur.type == 1) 20.sp else 16.sp) }
                }
                if (pick >= 0) {
                    Text(if (pick == cur.ans) "✔ 回答正确" else "✘ 回答错误", fontSize = 18.sp)
                    Text(cur.expl, fontSize = 16.sp)
                    val fav = st.favQ.any { it.id == cur.id }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { st.toggleQ(cur) }) { Text(if (fav) "★ 已收藏" else "☆ 收藏本题") }
                        Button(onClick = { next() }) { Text("下一题") }
                    }
                }
            }
        }
    }
}

@Composable
fun FavScreen(st: Store) {
    var t by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = t) {
            Tab(selected = t == 0, onClick = { t = 0 }, text = { Text("词句 (${st.favS.size})") })
            Tab(selected = t == 1, onClick = { t = 1 }, text = { Text("题目 (${st.favQ.size})") })
        }
        LazyColumn(Modifier.weight(1f).padding(8.dp)) {
            if (t == 0) {
                if (st.favS.isEmpty()) item { Text("暂无收藏，翻译结果右侧点 ☆ 收藏", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                items(st.favS.toList()) { SentCard(it, st) }
            } else {
                if (st.favQ.isEmpty()) item { Text("暂无收藏题目，做题答完后点「收藏本题」", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                items(st.favQ.toList()) { q ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(q.prompt, fontSize = 13.sp, color = Color.Gray)
                                Text(q.shown, fontSize = 20.sp)
                                Text("答案：" + q.options[q.ans], fontSize = 16.sp, color = Color(0xFF2E7D32))
                                Text(q.expl, fontSize = 13.sp, color = Color.Gray)
                            }
                            TextButton(onClick = { st.toggleQ(q) }) { Text("★", fontSize = 26.sp) }
                        }
                    }
                }
            }
        }
    }
}
