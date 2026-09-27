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
    fun autostartSplit(ctx: Context) = prefs(ctx).getBoolean("autostart_split", true)
    fun setAutostartSplit(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("autostart_split", v).apply()
    fun autostartMusic(ctx: Context) = prefs(ctx).getBoolean("autostart_music", true)
    fun setAutostartMusic(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("autostart_music", v).apply()

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
    var split by remember { mutableStateOf(KomPrefs.autostartSplit(context)) }
    var music by remember { mutableStateOf(KomPrefs.autostartMusic(context)) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.kom_autostart_title), color = TextPrimary, fontSize = 16.sp)
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.kom_autostart_split), color = TextPrimary, fontSize = 14.sp,
                    modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = split, onCheckedChange = { split = it; KomPrefs.setAutostartSplit(context, it) })
            }
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.kom_autostart_music), color = TextPrimary, fontSize = 14.sp,
                    modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = music, onCheckedChange = { music = it; KomPrefs.setAutostartMusic(context, it) })
            }
            Text(stringResource(R.string.kom_autostart_hint), color = TextMuted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}
