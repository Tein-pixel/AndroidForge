package com.teinzz.gamenuke

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.StatFs
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BG = Color(0xFF080B12)
private val CARD = Color(0xFF111720)
private val PANEL = Color(0xFF0C1219)
private val GREEN = Color(0xFF00FFC6)
private val DIM = Color(0xFF8B98A8)
private val RED = Color(0xFFFF4268)
private val TXT = Color(0xFFF4F7FA)
private val NAV = listOf("Tweaks", "Games", "Wipe", "Tasks", "Console")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 11, 18)
        window.navigationBarColor = android.graphics.Color.rgb(8, 11, 18)
        setContent { GameNukeApp() }
    }
}

private fun securePrefs(ctx: Context) = EncryptedSharedPreferences.create(
    ctx, "gamenuke_prefs",
    MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

private data class Telemetry(val ram: Int, val storage: Int, val used: String, val total: String, val temp: String)
private data class Tweak(val name: String, val desc: String, val on: String, val off: String, val note: String = "")
private data class Game(val name: String, val pkg: String)
private data class Proc(val name: String, val pid: Int, val mb: Int)

private val perf = listOf(
    Tweak("Performance Mode", "Minta Android Power HAL mengaktifkan fixed-performance mode.", "cmd power set-fixed-performance-mode-enabled true", "cmd power set-fixed-performance-mode-enabled false", "Tidak semua Android/ROM mendukungnya."),
    Tweak("Disable Doze", "Nonaktifkan Doze sementara selama bermain.", "dumpsys deviceidle disable", "dumpsys deviceidle enable", "Aktifkan kembali setelah selesai untuk menghemat baterai.")
)
private val graphics = listOf(
    Tweak("Force 120 Hz", "Minta refresh rate puncak 120 Hz.", "settings put system peak_refresh_rate 120; settings put system min_refresh_rate 120", "settings delete system peak_refresh_rate; settings delete system min_refresh_rate", "Tidak menambah refresh rate maksimum panel."),
    Tweak("Force 4x MSAA", "Ubah preferensi MSAA OpenGL untuk aplikasi yang mendukung.", "settings put global debug_egl_force_msaa 1", "settings put global debug_egl_force_msaa 0", "Dapat menambah beban GPU/baterai."),
    Tweak("Disable HW Overlays", "Minta SurfaceFlinger mematikan hardware overlays.", "service call SurfaceFlinger 1008 i32 1", "service call SurfaceFlinger 1008 i32 0", "Perintah bergantung pada ROM.")
)
private val memory = listOf(
    Tweak("Cached Apps Freezer", "Ubah kebijakan freezer aplikasi tersimpan jika didukung ROM.", "settings put global cached_apps_freezer enabled", "settings put global cached_apps_freezer disabled", "Tidak tersedia pada semua Android.")
)
private val network = listOf(
    Tweak("Cloudflare Private DNS", "Gunakan DNS-over-TLS Cloudflare.", "settings put global private_dns_mode hostname; settings put global private_dns_specifier one.one.one.one", "settings put global private_dns_mode opportunistic; settings delete global private_dns_specifier", "Jika jaringan memblokir DoT, kembali ke Opportunistic."),
    Tweak("TCP BBR (eksperimental)", "Minta algoritma congestion control BBR.", "sysctl -w net.ipv4.tcp_congestion_control=bbr", "sysctl -w net.ipv4.tcp_congestion_control=cubic", "Sering ditolak tanpa hak kernel/root.")
)

private fun telemetry(ctx: Context): Telemetry {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    val total = info.totalMem.coerceAtLeast(1L)
    val used = (total - info.availMem).coerceAtLeast(0)
    val fs = StatFs(android.os.Environment.getDataDirectory().absolutePath)
    val st = fs.totalBytes.coerceAtLeast(1L)
    val usedSt = st - fs.availableBytes
    val b = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val rawTemp = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
    return Telemetry((used * 100 / total).toInt(), (usedSt * 100 / st).toInt(),
        "%.1f GB".format(Locale.US, used / 1073741824.0), "%.1f GB".format(Locale.US, total / 1073741824.0),
        if (rawTemp >= 0) "%.1f°".format(Locale.US, rawTemp / 10f) else "—")
}

@Composable
private fun GameNukeApp() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { securePrefs(ctx) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var sub by remember { mutableIntStateOf(0) }
    var stats by remember { mutableStateOf(telemetry(ctx)) }
    var binder by remember { mutableStateOf(Shizuku.pingBinder()) }
    var allowed by remember { mutableStateOf(runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) }
    val logs = remember { mutableStateListOf<String>() }
    val games = remember { mutableStateListOf<Game>() }
    val processes = remember { mutableStateListOf<Proc>() }
    var filter by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var wipeProgress by remember { mutableFloatStateOf(0f) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        games.clear(); games.addAll(scanGames(ctx))
        while (true) {
            stats = telemetry(ctx); binder = Shizuku.pingBinder()
            allowed = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
            delay(2000)
        }
    }
    LaunchedEffect(notice) { if (notice.isNotBlank()) { snack.showSnackbar(notice); notice = "" } }
    MaterialTheme(colorScheme = darkColorScheme(primary = GREEN, onPrimary = BG, background = BG, onBackground = TXT, surface = CARD, onSurface = TXT, error = RED)) {
        Scaffold(containerColor = BG, snackbarHost = { SnackbarHost(snack) }, bottomBar = {
            NavigationBar(containerColor = Color(0xFF0A0E15), tonalElevation = 0.dp) {
                val icons = listOf(Icons.Filled.Speed, Icons.Filled.SportsEsports, Icons.Filled.CleaningServices, Icons.Filled.Memory, Icons.Filled.Terminal)
                NAV.forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icons[i], label, Modifier.size(21.dp)) }, label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = BG, selectedTextColor = GREEN, indicatorColor = GREEN,
                            unselectedIconColor = DIM, unselectedTextColor = DIM))
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(listOf("NUKE TWEAKER", "GAME PROFILES", "DEEP WIPE", "TASK MANAGER", "CONSOLE")[tab],
                            color = TXT, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("NUKE ENGINE  •  OMNI BOOSTER", color = DIM, fontSize = 9.sp, letterSpacing = 1.2.sp)
                    }
                    Box(Modifier.size(38.dp).clip(CircleShape).background(GREEN.copy(alpha = .12f)).clickable {
                        if (!binder) notice = "Mulai layanan Shizuku terlebih dahulu."
                        else if (!allowed) runCatching { Shizuku.requestPermission(4101) }.onFailure { notice = "Permintaan izin gagal." }
                        else notice = "Shizuku terhubung."
                    }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Bolt, "Shizuku", tint = GREEN) }
                }
                when (tab) {
                    0 -> Tweaks(stats, binder, allowed, sub, { sub = it }, prefs, logs, scope, { notice = it }, { busy = it }, { dialog = "thermal" })
                    1 -> GamesScreen(games, prefs, logs, scope, { notice = it })
                    2 -> WipeScreen(logs, scope, { notice = it }, { wipeProgress = it }, wipeProgress, busy, { busy = it })
                    3 -> TasksScreen(processes, filter, { filter = it }, logs, scope, { notice = it }, { busy = it }, { list -> processes.clear(); processes.addAll(list) }, { dialog = "nuke" })
                    else -> ConsoleScreen(logs, command, { command = it }, scope, { notice = it }, { busy = it })
                }
            }
        }
        if (dialog == "thermal") InfoDialog("Thermal Bypass diblokir", "Proteksi suhu tidak dimatikan. Menghentikan sensor/thermal daemon dapat menyebabkan overheating dan kerusakan perangkat. Telemetri suhu tetap tersedia.", { dialog = "" })
        if (dialog == "nuke") InfoDialog("Hentikan proses latar?", "Android akan diminta membunuh proses latar yang aman dihentikan. Aktivitas unduhan atau musik mungkin terganggu. Hasilnya akan dicatat di Console.", { dialog = ""; scope.launch { val cmd = "am kill-all"; val r = ShellRunner.execute(cmd); logs.add(logLine(cmd, r)); notice = r.output.take(160) } }, "JALANKAN")
    }
}

@Composable
private fun Tweaks(stats: Telemetry, binder: Boolean, allowed: Boolean, sub: Int, setSub: (Int) -> Unit,
                   prefs: android.content.SharedPreferences, logs: MutableList<String>, scope: kotlinx.coroutines.CoroutineScope,
                   notice: (String) -> Unit, setBusy: (Boolean) -> Unit, thermalInfo: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Metric("RAM", "${stats.ram}%", stats.used, Modifier.width(112.dp))
            Metric("STORAGE", "${stats.storage}%", "Data partition", Modifier.width(112.dp))
            Metric("BATTERY TEMP", stats.temp, "Battery sensor", Modifier.width(112.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Performance", "Graphics", "Memory", "Network").forEachIndexed { i, label ->
                FilterChip(sub == i, { setSub(i) }, label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GREEN, selectedLabelColor = BG, containerColor = CARD, labelColor = DIM), border = null)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Shizuku Shell", color = TXT, fontWeight = FontWeight.Bold)
                            Text(if (!binder) "Layanan tidak aktif" else if (!allowed) "Izin belum diberikan" else "Terhubung · izin dicek", color = if (binder && allowed) GREEN else RED, fontSize = 11.sp)
                        }
                        Button(onClick = {
                            if (!Shizuku.pingBinder()) notice("Jalankan Shizuku melalui ADB/Wireless debugging.")
                            else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) runCatching { Shizuku.requestPermission(4101) }.onFailure { notice("Izin gagal: ${it.message}") }
                            else notice("Shizuku siap.")
                        }, colors = ButtonDefaults.buttonColors(containerColor = GREEN, contentColor = BG), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text(if (allowed) "READY" else "IZINKAN", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            when (sub) {
                0 -> {
                    items(perf) { ToggleCard(it, prefs, logs, scope, notice, setBusy) }
                    item { PanelCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Thermal Bypass", color = TXT, fontWeight = FontWeight.SemiBold); Text("Diblokir · proteksi panas tetap aktif", color = RED, fontSize = 11.sp) }; TextButton(onClick = thermalInfo) { Text("INFO", color = GREEN) } } } }
                    item {
                        var cores by remember { mutableIntStateOf(prefs.getInt("cpu_cores", 4)) }
                        PanelCard {
                            Text("CPU Core Allocation", color = TXT, fontWeight = FontWeight.SemiBold)
                            Text("Target tersimpan; kernel affinity tidak dipaksakan jika ditolak.", color = DIM, fontSize = 11.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf(2, 4, 6, 8).forEach { n -> FilterChip(cores == n, { cores = n; prefs.edit().putInt("cpu_cores", n).apply(); notice("Target $n core tersimpan; tidak mengklaim perubahan kernel.") }, label = { Text("$n core") }, border = null) }
                            }
                        }
                    }
                    item { SimpleCard("CPU/GPU Kernel Boost", "Kernel governor, scheduler, dan clock boost biasanya membutuhkan root khusus vendor.", "INFO") { notice("Kontrol kernel tidak dijalankan jika shell tidak memiliki akses.") } }
                }
                1 -> {
                    items(graphics) { ToggleCard(it, prefs, logs, scope, notice, setBusy) }
                    item { SimpleCard("GPU Rendering", "Tidak ada perintah shell universal untuk memaksa semua game memakai renderer GPU tertentu.", "INFO") { notice("Renderer ditentukan Android dan driver vendor.") } }
                    item { SimpleCard("Disable VSYNC", "Tidak ditampilkan sebagai toggle karena layanan SurfaceFlinger berbeda antar-ROM.", "INFO") { notice("VSYNC generik tidak didukung secara universal.") } }
                }
                2 -> {
                    items(memory) { ToggleCard(it, prefs, logs, scope, notice, setBusy) }
                    item { SimpleCard("Clear Cached Processes", "am kill-all", "JALANKAN") { scope.launch { val c = "am kill-all"; val r = ShellRunner.execute(c); logs.add(logLine(c, r)); notice(r.output.take(160)) } } }
                    item { SimpleCard("Trim App Caches", "pm trim-caches 512M", "JALANKAN") { scope.launch { val c = "pm trim-caches 512M"; val r = ShellRunner.execute(c); logs.add(logLine(c, r)); notice(r.output.take(160)) } } }
                    item { SimpleCard("ZRAM / LMK Tuning", "Paramater kernel dapat tidak tersedia dan salah konfigurasi bisa membuat sistem tidak stabil.", "INFO") { notice("ZRAM/LMK otomatis dilewati tanpa validasi kernel.") } }
                }
                else -> {
                    items(network) { ToggleCard(it, prefs, logs, scope, notice, setBusy) }
                    item { SimpleCard("Ping Test", "Ukur 3 probe ke 1.1.1.1; hasil bergantung pada rute ISP.", "UJI") { scope.launch { val c = "ping -c 3 -W 2 1.1.1.1"; val r = ShellRunner.execute(c); logs.add(logLine(c, r)); notice(r.output.take(180)) } } }
                }
            }
        }
    }
}

@Composable
private fun ToggleCard(t: Tweak, prefs: android.content.SharedPreferences, logs: MutableList<String>, scope: kotlinx.coroutines.CoroutineScope, notice: (String) -> Unit, setBusy: (Boolean) -> Unit) {
    var checked by remember(t.name) { mutableStateOf(prefs.getBoolean("tweak:${t.name}", false)) }
    var loading by remember { mutableStateOf(false) }
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(t.name, color = TXT, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp)); Text(t.desc, color = DIM, fontSize = 11.sp, lineHeight = 15.sp)
                if (t.note.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(t.note, color = Color(0xFFB7C4D3), fontSize = 10.sp) }
            }
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = GREEN, strokeWidth = 2.dp)
            else Switch(checked, { next ->
                loading = true; setBusy(true)
                scope.launch {
                    val cmd = if (next) t.on else t.off
                    val r = ShellRunner.execute(cmd); logs.add(logLine(cmd, r))
                    if (r.success) { checked = next; prefs.edit().putBoolean("tweak:${t.name}", next).apply(); notice("${t.name}: ${if (next) "ON" else "OFF"}") }
                    else notice("${t.name} gagal: ${r.output.take(130)}")
                    loading = false; setBusy(false)
                }
            }, colors = SwitchDefaults.colors(checkedThumbColor = BG, checkedTrackColor = GREEN, uncheckedThumbColor = DIM, uncheckedTrackColor = PANEL))
        }
    }
}

@Composable
private fun GamesScreen(games: List<Game>, prefs: android.content.SharedPreferences, logs: MutableList<String>, scope: kotlinx.coroutines.CoroutineScope, notice: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PanelCard { Text("ESPORTS DASHBOARD", color = GREEN, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(5.dp)); Text("${games.size} game terdeteksi", color = TXT, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text("Pemindaian kategori game + nama/paket fallback.", color = DIM, fontSize = 11.sp) } }
        if (games.isEmpty()) item { EmptyCard("Belum ada game", "Kategori aplikasi Android menentukan hasil pemindaian.") }
        items(games, key = { it.pkg }) { game ->
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(GREEN.copy(alpha = .1f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.SportsEsports, null, tint = GREEN) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game.name, color = TXT, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(game.pkg, color = DIM, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Mode: ${prefs.getString("mode:${game.pkg}", "Balanced")}", color = GREEN, fontSize = 10.sp)
                    }
                    Button(onClick = {
                        scope.launch {
                            val c = "pm trim-caches 128M"; val r = ShellRunner.execute(c); logs.add(logLine(c, r))
                            val launch = game.pkg.let { (androidx.compose.ui.platform.LocalContext.current.packageManager.getLaunchIntentForPackage(it)) }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = GREEN, contentColor = BG), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Icon(Icons.Filled.Bolt, null, Modifier.size(15.dp)); Text(" BOOST", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item { Text("BOOST meminta trim cache. Izinkan overlay melalui Android Settings sebelum memakai HUD.", color = DIM, fontSize = 10.sp) }
    }
}

private fun scanGames(ctx: Context): List<Game> {
    val hints = setOf("com.dts.freefireth", "com.mobile.legends", "com.tencent.ig", "com.pubg.imobile", "com.garena.game.codm", "com.block.juggle", "company.gamesx.guitarflash", "br.com.rodrigokolb.realdru")
    return runCatching { ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).mapNotNull { a ->
        if (a.packageName == ctx.packageName) return@mapNotNull null
        val name = ctx.packageManager.getApplicationLabel(a).toString()
        val hinted = a.packageName in hints || hints.any { a.packageName.startsWith(it) }
        val keyword = listOf("game", "mobile legends", "free fire", "pubg", "genshin", "wild rift", "block blast", "guitar flash", "real drum", "roblox", "racing", "arena").any { name.contains(it, true) }
        val categorized = a.category == ApplicationInfo.CATEGORY_GAME
        if ((hinted || keyword || categorized) && ((a.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || categorized || hinted)) Game(name, a.packageName) else null
    }.distinctBy { it.pkg }.sortedBy { it.name.lowercase(Locale.getDefault()) } }.getOrDefault(emptyList())
}

@Composable
private fun WipeScreen(logs: MutableList<String>, scope: kotlinx.coroutines.CoroutineScope, notice: (String) -> Unit, setProgress: (Float) -> Unit, progress: Float, busy: Boolean, setBusy: (Boolean) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            PanelCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(94.dp).clip(CircleShape).background(GREEN.copy(alpha = .1f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.CleaningServices, null, tint = GREEN, modifier = Modifier.size(46.dp)) }
                        Spacer(Modifier.height(14.dp)); Text("System Ready to Wipe", color = TXT, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Trim cache dan bersihkan log diagnostik yang dipilih.", color = DIM, fontSize = 11.sp)
                        if (busy) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator({ progress }, Modifier.fillMaxWidth(), color = GREEN) }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            if (busy) return@Button
                            setBusy(true); setProgress(0f)
                            scope.launch {
                                var ok = 0
                                listOf("pm trim-caches 512M", "logcat -b all -c").forEachIndexed { i, c ->
                                    val r = ShellRunner.execute(c); logs.add(logLine(c, r)); if (r.success) ok++
                                    setProgress((i + 1) / 2f)
                                }
                                setBusy(false); notice("Selesai: $ok/2 perintah berhasil. Direktori sistem tidak dihapus.")
                            }
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = GREEN, contentColor = BG)) { Text(if (busy) "PROSES…" else "START DEEP WIPE", fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }
        }
        item { SimpleCard("Wipe System Cache", "pm trim-caches 512M", "JALANKAN") { scope.launch { val c = "pm trim-caches 512M"; val r = ShellRunner.execute(c); logs.add(logLine(c, r)); notice(r.output.take(160)) } } }
        item { SimpleCard("Clear Logcat Buffers", "logcat -b all -c", "JALANKAN") { scope.launch { val c = "logcat -b all -c"; val r = ShellRunner.execute(c); logs.add(logLine(c, r)); notice(r.output.take(160)) } } }
        item { Text("Direktori /data, /cache, /tombstones dan /anr tidak dihapus secara massal.", color = DIM, fontSize = 10.sp) }
    }
}

@Composable
private fun TasksScreen(items: List<Proc>, filter: String, setFilter: (String) -> Unit, logs: MutableList<String>, scope: kotlinx.coroutines.CoroutineScope,
                        notice: (String) -> Unit, setBusy: (Boolean) -> Unit, setItems: (List<Proc>) -> Unit, askNuke: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val visible = items.filter { it.name.contains(filter, true) }
    LaunchedEffect(Unit) { setItems(scanProcesses(ctx)) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Active App Processes", color = TXT, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Daftar proses dari Shizuku shell.", color = DIM, fontSize = 11.sp) }; Text("${items.size}", color = GREEN, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(PANEL).padding(11.dp)) {
                        if (filter.isEmpty()) Text("Filter package name…", color = DIM, fontSize = 12.sp)
                        BasicTextField(filter, setFilter, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = TXT, fontSize = 12.sp), cursorBrush = SolidColor(GREEN))
                    }
                    Spacer(Modifier.width(7.dp))
                    Button(onClick = { scope.launch { setItems(scanProcesses(ctx)); notice("Pemindaian proses selesai.") } }, colors = ButtonDefaults.buttonColors(containerColor = PANEL, contentColor = GREEN)) { Text("⟳") }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = askNuke, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = RED, contentColor = TXT)) { Text("⚠  NUKE BACKGROUND APPS", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp) }
            }
        }
        if (visible.isEmpty()) item { EmptyCard("Tidak ada data", "Berikan izin Shizuku, lalu tekan refresh.") }
        items(visible, key = { "${it.name}:${it.pid}" }) { p ->
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Memory, null, tint = GREEN)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) { Text(p.name, color = TXT, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("PID ${p.pid} · ${p.mb} MB RSS", color = DIM, fontSize = 10.sp) }
                    TextButton(onClick = {
                        val pkg = p.name.substringBefore(':')
                        if (pkg !in scanPackageNames(ctx) || p.name.contains("system_server") || p.name.contains("systemui") || pkg == ctx.packageName) notice("Proses sistem/aplikasi tidak aman tidak dihentikan.")
                        else scope.launch { val c = "am force-stop ${shellQuote(pkg)}"; val r = ShellRunner.execute(c); logs.add(logLine(c, r)); notice(if (r.success) "Permintaan force-stop dikirim." else r.output.take(140)) }
                    }) { Text("✕", color = RED, fontSize = 18.sp) }
                }
            }
        }
    }
}

private suspend fun scanProcesses(ctx: Context): List<Proc> {
    val r = ShellRunner.execute("ps -A -o NAME,PID,RSS")
    if (!r.success) return emptyList()
    val packages = withContext(Dispatchers.IO) { scanPackageNames(ctx) }
    return r.output.lineSequence().drop(1).mapNotNull { line ->
        val m = Regex("^\\s*(\\S+)\\s+(\\d+)\\s+(\\d+)\\s*$").find(line) ?: return@mapNotNull null
        val name = m.groupValues[1]; val pkg = name.substringBefore(':')
        val pid = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        val rss = m.groupValues[3].toIntOrNull() ?: 0
        if (pkg !in packages || pkg == ctx.packageName) null else Proc(name, pid, rss / 1024)
    }.distinctBy { it.pid }.sortedByDescending { it.mb }.take(80).toList()
}
private fun scanPackageNames(ctx: Context) = runCatching { ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }.map { it.packageName }.toSet() }.getOrDefault(emptySet())

@Composable
private fun ConsoleScreen(logs: MutableList<String>, command: String, setCommand: (String) -> Unit, scope: kotlinx.coroutines.CoroutineScope, notice: (String) -> Unit, setBusy: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        PanelCard(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("SHELL CONSOLE", color = GREEN, fontSize = 10.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); TextButton(onClick = { logs.clear() }) { Text("CLEAR", color = DIM, fontSize = 10.sp) } }
            LazyColumn(Modifier.fillMaxSize(), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (logs.isEmpty()) item { Text("Console kosong. Perintah dan hasil shell muncul di sini.", color = DIM, fontSize = 12.sp) }
                items(logs.toList().reversed()) { line -> Text(line, color = if (line.contains("[OK]")) GREEN else if (line.contains("[ERR]")) RED else Color(0xFFB9C7D5), fontSize = 10.sp, lineHeight = 14.sp) }
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(CARD).padding(12.dp)) {
                if (command.isEmpty()) Text("dumpsys meminfo…", color = DIM, fontSize = 12.sp)
                BasicTextField(command, setCommand, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = TXT, fontSize = 12.sp), cursorBrush = SolidColor(GREEN))
            }
            Spacer(Modifier.width(7.dp))
            Button(onClick = {
                val c = command.trim()
                if (c.isBlank()) return@Button
                if (listOf("rm -rf", "reboot", "shutdown", "pm uninstall", "thermalservice override", "thermal-daemon", "thermald", "drop_caches", "/sys/class/thermal").any { c.contains(it, true) }) { notice("Perintah diblokir demi keselamatan perangkat."); return@Button }
                setCommand(""); setBusy(true)
                scope.launch { val r = ShellRunner.execute(c); logs.add(logLine(c, r)); notice(if (r.success) "Perintah selesai." else "Gagal: ${r.output.take(100)}"); setBusy(false) }
            }, colors = ButtonDefaults.buttonColors(containerColor = GREEN, contentColor = BG)) { Icon(Icons.Filled.Code, null) }
        }
        Spacer(Modifier.height(7.dp))
        Text("Perintah penghapusan sistem, reboot, uninstall, dan thermal bypass diblokir.", color = DIM, fontSize = 9.sp)
        Spacer(Modifier.height(7.dp))
    }
}

@Composable private fun Metric(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    PanelCard(modifier) { Text(label, color = DIM, fontSize = 9.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text(value, color = if (label == "BATTERY TEMP") RED else TXT, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text(detail, color = DIM, fontSize = 9.sp, maxLines = 1) }
}
@Composable private fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = CARD, shape = RoundedCornerShape(16.dp), tonalElevation = 0.dp) { Column(Modifier.padding(14.dp), content = content) }
}
@Composable private fun SimpleCard(title: String, body: String, action: String, onClick: () -> Unit) {
    PanelCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = TXT, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(body, color = DIM, fontSize = 11.sp) }; TextButton(onClick = onClick) { Text(action, color = GREEN, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } }
}
@Composable private fun EmptyCard(title: String, body: String) { PanelCard { Text(title, color = TXT, fontWeight = FontWeight.Bold); Text(body, color = DIM, fontSize = 11.sp) } }
@Composable private fun InfoDialog(title: String, body: String, close: () -> Unit, confirm: String = "MENGERTI") {
    AlertDialog(onDismissRequest = close, title = { Text(title, color = TXT, fontWeight = FontWeight.Bold) }, text = { Text(body, color = DIM, fontSize = 12.sp) },
        confirmButton = { TextButton(onClick = close) { Text(confirm, color = GREEN, fontWeight = FontWeight.Bold) } }, containerColor = CARD)
}
private fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"
private fun logLine(command: String, result: ShellResult): String {
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    return "[$time] ${if (result.success) "[OK]" else "[ERR]"} $ $command\n${result.output.take(1000)} (exit ${result.exitCode})"
}
