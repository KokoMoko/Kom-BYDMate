package com.bydmate.app.ui.dashboard

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary

/** Kom-BYDMate: օգտատիրոջ անունը՝ Главная-ի վերնագրի համար («Welcome Kom»)։ */
object KomPrefs {
    private const val PREFS = "kom_prefs"
    const val KEY_WELCOME_NAME = "welcome_name"
    const val MAX_NAME = 20

    // Միշտ applicationContext-ով․ Settings-ի և Главная-ի LocalContext-ները կարող են տարբեր լինել
    // (localized / device-protected context), և այդ դեպքում կկարդային տարբեր ֆայլեր։
    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    // Կրկնօրինակ՝ պարզ ֆայլում (filesDir)․ եթե prefs-ը ինչ-որ պատճառով դատարկ է, կարդում ենք ֆայլից
    private fun file(ctx: Context) = java.io.File(ctx.applicationContext.filesDir, "kom_welcome_name.txt")

    fun welcomeName(ctx: Context): String {
        val fromPrefs = prefs(ctx).getString(KEY_WELCOME_NAME, null)
        if (!fromPrefs.isNullOrEmpty()) return fromPrefs
        return runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
    }

    fun setWelcomeName(ctx: Context, name: String) {
        val v = name.take(MAX_NAME)
        prefs(ctx).edit().putString(KEY_WELCOME_NAME, v).commit()
        runCatching { file(ctx).writeText(v) }
    }

    // Մեքենան միացնելիս՝ split Navigator-ով և Yandex Music-ի միացում (տես KomAutostart)
    /** Մեքենայի համարանիշը Cluster-ի մեքենայի պատկերի համար (լռելյայն՝ դատարկ)։ */
    fun plate(ctx: Context): String = prefs(ctx).getString("license_plate", "") ?: ""
    fun setPlate(ctx: Context, v: String) { prefs(ctx).edit().putString("license_plate", v.take(12)).commit() }

    fun autostartNavi(ctx: Context) = prefs(ctx).getBoolean("autostart_navi", true)
    fun setAutostartNavi(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("autostart_navi", v).apply()
    fun autostartMusicPlay(ctx: Context) = prefs(ctx).getBoolean("autostart_music_play", true)
    fun setAutostartMusicPlay(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("autostart_music_play", v).apply()
    fun autostartMusic(ctx: Context) = prefs(ctx).getBoolean("autostart_music", true)
    fun setAutostartMusic(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("autostart_music", v).apply()
    /** Վարորդի էկրանին Navigator-ի ինքնաբերական վերադարձը՝ վայրկյաններ (0՝ անջատված)։ */
    fun clusterReturnSec(ctx: Context) = prefs(ctx).getInt("cluster_return_sec", 5)
    fun setClusterReturnSec(ctx: Context, v: Int) = prefs(ctx).edit().putInt("cluster_return_sec", v).apply()
    /** Ձայնային ազդանշան՝ սահմանափակումը + շեղումը գերազանցելիս։ */
    fun speedAlert(ctx: Context) = prefs(ctx).getBoolean("speed_alert", true)
    fun setSpeedAlert(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("speed_alert", v).apply()

    /** Վերնագիրը՝ ըստ պահված անվան (օգտագործվում է և՛ Dashboard-ում, և՛ Settings-ի նախադիտման մեջ)։ */
    fun titleFor(name: String): String? =
        name.trim().removePrefix("Welcome ").removePrefix("welcome ").trim().ifEmpty { null }
}

/** Главная-ի վերնագիրը․ «Welcome <անուն>», իսկ անուն չլինելիս՝ «MyBYD»։ Թարմանում է անմիջապես։ */
@Composable
fun rememberDashboardTitle(): String {
    val context = LocalContext.current
    // Կարդում ենք պահոցից 2 վայրկյանը մեկ (listener-ից անկախ՝ ավելի հուսալի)
    val name by produceState(initialValue = KomPrefs.welcomeName(context)) {
        while (true) {
            value = KomPrefs.welcomeName(context)
            delay(2_000L)
        }
    }
    // Եթե օգտատերը ինքն է գրել «Welcome …», կրկին չենք ավելացնում
    val trimmed = KomPrefs.titleFor(name)
    return if (trimmed == null) "MyBYD" else stringResource(R.string.kom_welcome_title, trimmed)
}

/** Settings → Application՝ «Welcome name» դաշտը։ */
@Composable
fun KomWelcomeNameBlock() {
    val context = LocalContext.current
    var value by remember { mutableStateOf(KomPrefs.welcomeName(context)) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = { v ->
                    value = v.take(KomPrefs.MAX_NAME)
                    KomPrefs.setWelcomeName(context, value)
                },
                label = { Text(stringResource(R.string.kom_settings_welcome_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.kom_settings_welcome_hint), color = TextMuted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp))
            // Նախադիտում՝ ինչ է իրականում պահված (ստուգման համար)
            val saved = remember(value) { KomPrefs.welcomeName(context) }
            val t = KomPrefs.titleFor(saved)
            Text(
                "Dashboard: " + (if (t == null) "MyBYD" else stringResource(R.string.kom_welcome_title, t)) +
                    "  (saved: “" + saved + "”)",
                color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Settings → Application՝ մեքենան միացնելիս ինչ բացել։ */
@Composable
fun KomAutostartBlock() {
    val context = LocalContext.current
    var navi by remember { mutableStateOf(KomPrefs.autostartNavi(context)) }
    var music by remember { mutableStateOf(KomPrefs.autostartMusic(context)) }
    var play by remember { mutableStateOf(KomPrefs.autostartMusicPlay(context)) }
    // Նոր օգտատեր՝ առանց Yandex Navigator / Music․ փոխարկիչն անջատված է և նշված «տեղադրված չէ»
    val naviOk = remember { KomAutostart.isInstalled(context, KomAutostart.NAVI_PKG) }
    val musicOk = remember { KomAutostart.isInstalled(context, KomAutostart.MUSIC_PKG) }
    val notInstalled = " · " + stringResource(R.string.kom_not_installed)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.kom_autostart_title), color = TextPrimary, fontSize = 16.sp)
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.kom_autostart_navi) + if (naviOk) "" else notInstalled,
                    color = if (naviOk) TextPrimary else TextMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = navi && naviOk, enabled = naviOk,
                    onCheckedChange = { navi = it; KomPrefs.setAutostartNavi(context, it) })
            }
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.kom_autostart_music) + if (musicOk) "" else notInstalled,
                    color = if (musicOk) TextPrimary else TextMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = music && musicOk, enabled = musicOk,
                    onCheckedChange = { music = it; KomPrefs.setAutostartMusic(context, it) })
            }
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.kom_autostart_music_play), color = if (music && musicOk) TextPrimary else TextMuted,
                    fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
                androidx.compose.material3.Switch(checked = play && musicOk, enabled = music && musicOk,
                    onCheckedChange = { play = it; KomPrefs.setAutostartMusicPlay(context, it) })
            }
            Text(stringResource(R.string.kom_autostart_hint), color = TextMuted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Settings → Application՝ վարորդի էկրանին Navigator-ի վերադարձը քարտը թերթելուց հետո։ */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun KomClusterReturnBlock() {
    val context = LocalContext.current
    var sec by remember { mutableStateOf(KomPrefs.clusterReturnSec(context)) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.kom_cluster_return_title), color = TextPrimary, fontSize = 16.sp)
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                listOf(0, 5, 10, 15).forEach { v ->
                    androidx.compose.material3.FilterChip(
                        selected = sec == v,
                        onClick = { sec = v; KomPrefs.setClusterReturnSec(context, v) },
                        label = { Text(if (v == 0) stringResource(R.string.kom_off) else "$v s") },
                    )
                }
            }
            Text(stringResource(R.string.kom_cluster_return_hint), color = TextMuted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Settings → Application՝ արագությունը գերազանցելիս ձայնային ազդանշան։ */
@Composable
fun KomSpeedAlertBlock() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(KomPrefs.speedAlert(context)) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.kom_speed_alert_title), color = TextPrimary, fontSize = 16.sp,
                    modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { KomSpeedAlert.test() }) {
                    Text(stringResource(R.string.kom_speed_alert_test))
                }
                androidx.compose.material3.Switch(checked = on, onCheckedChange = { on = it; KomPrefs.setSpeedAlert(context, it) })
            }
            Text(stringResource(R.string.kom_speed_alert_hint), color = TextMuted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Settings → Application՝ ոստիկանը ճանապարհին (սահմանափակումը 30+ վրկ գերազանցելիս)։ */
@Composable
fun KomPoliceBlock() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(KomPolice.enabled(context)) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.kom_police_title), color = TextPrimary, fontSize = 16.sp,
                    modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { KomPolice.whistle() }) {
                    Text(stringResource(R.string.kom_speed_alert_test))
                }
                androidx.compose.material3.Switch(checked = on, onCheckedChange = { on = it; KomPolice.setEnabled(context, it) })
            }
            Text(stringResource(R.string.kom_police_hint), color = TextMuted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Settings → Application՝ «License plate» դաշտը (Cluster-ի մեքենայի համարանիշը)։ */
@Composable
fun KomPlateBlock() {
    val context = LocalContext.current
    var value by remember { mutableStateOf(KomPrefs.plate(context)) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = { v ->
                    value = v.uppercase().take(12)
                    KomPrefs.setPlate(context, value)
                },
                label = { Text(stringResource(R.string.kom_settings_plate)) },
                placeholder = { Text("00 AA 000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.kom_settings_plate_hint), color = TextMuted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}
