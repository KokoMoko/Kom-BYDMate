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
    fun welcomeName(ctx: Context): String = prefs(ctx).getString(KEY_WELCOME_NAME, "") ?: ""
    fun setWelcomeName(ctx: Context, name: String) {
        prefs(ctx).edit().putString(KEY_WELCOME_NAME, name.take(MAX_NAME)).commit()
    }
}

/** Главная-ի վերնագիրը․ «Welcome <անուն>», իսկ անուն չլինելիս՝ «MyBYD»։ Թարմանում է անմիջապես։ */
@Composable
fun rememberDashboardTitle(): String {
    val context = LocalContext.current
    var name by remember { mutableStateOf(KomPrefs.welcomeName(context)) }
    DisposableEffect(context) {
        val prefs = KomPrefs.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KomPrefs.KEY_WELCOME_NAME) name = KomPrefs.welcomeName(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    // Եթե օգտատերը ինքն է գրել «Welcome …», կրկին չենք ավելացնում
    val trimmed = name.trim().removePrefix("Welcome ").removePrefix("welcome ").trim()
    return if (trimmed.isEmpty()) "MyBYD" else stringResource(R.string.kom_welcome_title, trimmed)
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
        }
    }
}
