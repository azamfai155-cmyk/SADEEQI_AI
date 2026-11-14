package com.azzam.sadeeqi.trading

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.azzam.sadeeqi.memory.EncryptedMemory
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

private val Gold = Color(0xFFE6C77A)
private val Panel = Color(0xE626293A)

data class BacktestStats(
    val trades: Int,
    val wins: Int,
    val losses: Int,
    val winRatePercent: Double,
    val profitFactor: Double?
)

object TradingMath {
    fun riskAmount(balance: Double, riskPercent: Double): Double {
        require(balance > 0.0 && riskPercent in 0.01..100.0)
        return balance * riskPercent / 100.0
    }

    fun positionSizeByStop(riskAmount: Double, stopDistance: Double, valuePerUnit: Double = 1.0): Double {
        require(riskAmount > 0.0 && stopDistance > 0.0 && valuePerUnit > 0.0)
        return riskAmount / (stopDistance * valuePerUnit)
    }

    fun backtest(wins: Int, losses: Int, averageWinR: Double, averageLossR: Double): BacktestStats {
        require(wins >= 0 && losses >= 0 && wins + losses > 0)
        require(averageWinR >= 0.0 && averageLossR >= 0.0)
        val total = wins + losses
        val grossProfit = wins * averageWinR
        val grossLoss = losses * averageLossR
        val pf = if (grossLoss == 0.0) null else grossProfit / grossLoss
        return BacktestStats(total, wins, losses, wins.toDouble() / total * 100.0, pf)
    }

    fun rewardRisk(entry: Double, stop: Double, target: Double): Double {
        val risk = abs(entry - stop)
        require(risk > 0.0)
        return abs(target - entry) / risk
    }
}

private data class TradingLesson(
    val id: String,
    val title: String,
    val channel: String,
    val url: String,
    val notes: String
)

@Composable
fun TradingIntelligenceScreen(memory: EncryptedMemory, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf("المكتبة") }
    var title by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("Hermes Trading") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var lessons by remember { mutableStateOf(emptyList<TradingLesson>()) }
    var notice by remember { mutableStateOf("احفظ دروسك وملاحظاتك ومصادرها في ذاكرة صديقي المشفرة على هذا الجهاز.") }

    var balance by remember { mutableStateOf("1000") }
    var riskPct by remember { mutableStateOf("1") }
    var stopDistance by remember { mutableStateOf("20") }
    var entry by remember { mutableStateOf("1.0800") }
    var stop by remember { mutableStateOf("1.0780") }
    var target by remember { mutableStateOf("1.0840") }
    var wins by remember { mutableStateOf("20") }
    var losses by remember { mutableStateOf("10") }
    var avgWin by remember { mutableStateOf("2") }
    var avgLoss by remember { mutableStateOf("1") }
    var calculation by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val saved = memory.search("trading:lesson:")
        lessons = saved.mapNotNull { (id, value) ->
            runCatching {
                val j = JSONObject(value)
                TradingLesson(id, j.getString("title"), j.getString("channel"), j.optString("url"), j.getString("notes"))
            }.getOrNull()
        }.sortedByDescending { it.id }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SADEEQI TRADING INTELLIGENCE", color = Gold, style = MaterialTheme.typography.titleLarge)
        Text("مركز تعلم وتحليل وإدارة مخاطر — الإصدار الأول", color = Color.LightGray)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("المكتبة", "مدرب SMC", "المخاطرة", "Backtest").forEach { item ->
                FilterChip(selected = section == item, onClick = { section = item }, label = { Text(item) })
            }
        }
        when (section) {
            "المكتبة" -> {
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("حفظ درس ومصدره", color = Gold, style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان الدرس") }, singleLine = true)
                        OutlinedTextField(channel, { channel = it }, Modifier.fillMaxWidth(), label = { Text("القناة / المدرّس") }, singleLine = true)
                        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("رابط فيديو YouTube أو المصدر") }, singleLine = true)
                        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 4, label = { Text("الصق التفريغ أو ملاحظات الدرس هنا") })
                        Button(
                            onClick = {
                                if (title.isBlank() || notes.isBlank()) {
                                    notice = "أدخل عنوان الدرس ومحتواه أولاً."
                                } else {
                                    val id = "trading:lesson:${System.currentTimeMillis()}:${UUID.randomUUID()}"
                                    val json = JSONObject().put("title", title.trim()).put("channel", channel.trim())
                                        .put("url", url.trim()).put("notes", notes.trim()).toString()
                                    scope.launch {
                                        runCatching { memory.save(id, "trading_lesson", json) }
                                            .onSuccess {
                                                lessons = lessons + TradingLesson(id, title.trim(), channel.trim(), url.trim(), notes.trim())
                                                title = ""; url = ""; notes = ""
                                                notice = "تم حفظ الدرس في الذاكرة المشفرة على هذا الجهاز."
                                            }
                                            .onFailure { notice = "تعذر حفظ الدرس: ${it.message}" }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E6B35))
                        ) { Text("حفظ الدرس") }
                        Text(notice, color = Color(0xFFD2D0DC), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("الدروس المحفوظة (${lessons.size})", color = Gold, style = MaterialTheme.typography.titleMedium)
                lessons.forEach { lesson ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(lesson.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text("المصدر: ${lesson.channel}", color = Gold)
                            if (lesson.url.isNotBlank()) Text(lesson.url, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            Text(lesson.notes, color = Color.White, maxLines = 8)
                            TextButton(onClick = {
                                scope.launch {
                                    memory.delete(lesson.id)
                                    lessons = lessons.filterNot { it.id == lesson.id }
                                    notice = "حُذف الدرس من ذاكرة التداول."
                                }
                            }) { Text("حذف الدرس", color = Color(0xFFFF9A9A)) }
                        }
                    }
                }
                Text("ملاحظة: لا يتم جلب فيديو YouTube أو تفريغه تلقائياً في هذا الإصدار. أضف التفريغ المتاح أو ملاحظاتك مع رابط المصدر؛ لن ننسب معلومة إلى فيديو لم نصل إلى محتواه.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
            "مدرب SMC" -> {
                Text("قاموس المفاهيم الأساسية", color = Gold, style = MaterialTheme.typography.titleMedium)
                val terms = listOf(
                    "Market Structure — بنية السوق: تسلسل القمم والقيعان الذي يصف حركة السعر.",
                    "BOS — كسر البنية: تجاوز قمة/قاع بنيوي بما يدعم استمرار الاتجاه وفق قواعد التحليل.",
                    "MSS — تحول بنية السوق: تغير محتمل في السلوك البنيوي بعد إزاحة وكسر مستوى مهم.",
                    "Liquidity — السيولة: أوامر متجمعة غالباً قرب القمم والقيعان الواضحة.",
                    "BSL / SSL — سيولة الشراء / البيع: تجمعات أوامر أعلى القمم أو أسفل القيعان.",
                    "Liquidity Sweep — سحب السيولة: اختراق مستوى واضح ثم عودة السعر؛ ليس انعكاساً مؤكداً وحده.",
                    "Order Block — كتلة أوامر: منطقة سعرية يعرّفها المنهج وفق حركة الإزاحة السابقة.",
                    "FVG — فجوة قيمة عادلة: اختلال ثلاث شمعات يترك نطاقاً سعرياً غير متداخل.",
                    "Premium / Discount — علاوة / خصم: موقع السعر فوق أو تحت منتصف نطاق مرجعي.",
                    "POI — منطقة اهتمام: نطاق تراقبه بحثاً عن شروط تأكيد، لا إشارة دخول تلقائية."
                )
                terms.forEach { term ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Text(term, Modifier.padding(12.dp), color = Color.White)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("اختبار سريع", color = Gold, style = MaterialTheme.typography.titleMedium)
                        Text("هل يكفي ظهور Liquidity Sweep وحده للدخول؟", color = Color.White)
                        var answer by remember { mutableStateOf("") }
                        var show by remember { mutableStateOf(false) }
                        listOf("نعم، دائماً", "لا، نحتاج سياقاً وتأكيداً").forEach {
                            Row {
                                RadioButton(selected = answer == it, onClick = { answer = it })
                                Text(it, Modifier.padding(top = 12.dp), color = Color.White)
                            }
                        }
                        Button(onClick = { show = true }) { Text("تحقق من الإجابة") }
                        if (show) Text(if (answer == "لا، نحتاج سياقاً وتأكيداً") "صحيح. نحتاج سياق الإطار الأعلى وشروط تأكيد وإبطال واضحة." else "ليست الإجابة الصحيحة. السحب وحده لا يثبت انعكاساً.", color = Gold)
                    }
                }
            }
            "المخاطرة" -> {
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("حاسبة المخاطرة وحجم المركز", color = Gold, style = MaterialTheme.typography.titleMedium)
                        NumberField("رصيد الحساب", balance) { balance = it }
                        NumberField("المخاطرة %", riskPct) { riskPct = it }
                        NumberField("مسافة وقف الخسارة (وحدة سعرية)", stopDistance) { stopDistance = it }
                        Button(onClick = {
                            calculation = runCatching {
                                val amount = TradingMath.riskAmount(balance.toDouble(), riskPct.toDouble())
                                val size = TradingMath.positionSizeByStop(amount, stopDistance.toDouble())
                                "المبلغ المعرض للمخاطرة: %.2f\nحجم المركز النظري: %.4f وحدة".format(amount, size)
                            }.getOrElse { "تحقق من إدخال أرقام موجبة وصحيحة." }
                        }) { Text("احسب") }
                        calculation?.let { Text(it, color = Color.White) }
                        Text("الحجم هنا بوحدات نظرية فقط. تحويله إلى lots أو عقود يتطلب معرفة قيمة النقطة وحجم العقد وعملة الحساب لدى وسيطك.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Divider(color = Color.DarkGray)
                        Text("حساب العائد إلى المخاطرة", color = Gold)
                        NumberField("سعر الدخول", entry) { entry = it }
                        NumberField("وقف الخسارة", stop) { stop = it }
                        NumberField("الهدف", target) { target = it }
                        Button(onClick = {
                            calculation = runCatching {
                                "Risk/Reward = 1 : %.2f".format(TradingMath.rewardRisk(entry.toDouble(), stop.toDouble(), target.toDouble()))
                            }.getOrElse { "تأكد أن الدخول ووقف الخسارة والهدف أرقام، وأن الوقف لا يساوي الدخول." }
                        }) { Text("احسب R:R") }
                    }
                }
            }
            else -> {
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ملخص نتائج اختبار تاريخي", color = Gold, style = MaterialTheme.typography.titleMedium)
                        NumberField("عدد الصفقات الرابحة", wins) { wins = it }
                        NumberField("عدد الصفقات الخاسرة", losses) { losses = it }
                        NumberField("متوسط الربح بوحدة R", avgWin) { avgWin = it }
                        NumberField("متوسط الخسارة بوحدة R", avgLoss) { avgLoss = it }
                        Button(onClick = {
                            calculation = runCatching {
                                val s = TradingMath.backtest(wins.toInt(), losses.toInt(), avgWin.toDouble(), avgLoss.toDouble())
                                "عدد الصفقات: ${s.trades}\nWin Rate: %.2f%%\nProfit Factor: %s".format(
                                    s.winRatePercent, s.profitFactor?.let { "%.2f".format(it) } ?: "غير معرّف (لا خسائر مسجلة)"
                                )
                            }.getOrElse { "أدخل أعداداً صحيحة غير سالبة ومتوسطات R صحيحة، ومجموع صفقات أكبر من صفر." }
                        }) { Text("احسب النتائج") }
                        calculation?.let { Text(it, color = Color.White) }
                        Text("هذه حاسبة إحصائية لنتائج تدخلها يدوياً، وليست محرك Backtesting متصلاً ببيانات تاريخية. لا تحسب Drawdown أو الانزلاق أو العمولات بعد.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true
    )
}
