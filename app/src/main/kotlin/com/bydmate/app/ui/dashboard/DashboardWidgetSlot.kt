package com.bydmate.app.ui.dashboard

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.NavyDark
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

/**
 * Kom-BYDMate: Главная-ի widget-ի «սլոտներ»՝ ցանկացած տեղադրված Android widget
 * (օր․ AccuWeather, Yandex Music) հավելվածի ներսում։ Յուրաքանչյուր սլոտ հիշում է իր widget id-ն։
 */
object DashboardWidgets {
    const val HOST_ID = 7300
    const val SLOT_LEFT = "left"    // A՝ ձախ ներքև (Yandex Music)
    const val SLOT_RIGHT = "right"  // աջ ներքև (եղանակ)
    private const val PREFS = "kom_dashboard_widgets"

    @Volatile private var host: AppWidgetHost? = null

    fun host(ctx: Context): AppWidgetHost =
        host ?: synchronized(this) {
            host ?: AppWidgetHost(ctx.applicationContext, HOST_ID).also { host = it }
        }

    fun get(ctx: Context, slot: String): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(slot, -1)

    fun set(ctx: Context, slot: String, id: Int) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(slot, id).apply()

    fun clear(ctx: Context, slot: String) {
        val id = get(ctx, slot)
        if (id != -1) runCatching { host(ctx).deleteAppWidgetId(id) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(slot).apply()
    }
}

/**
 * Widget-ի սլոտ։ Դատարկ ժամանակ՝ «＋ [emptyHint]», սեղմելիս՝ widget-ների ցանկ։
 * [requestGrant]-ը ADB-ով տալիս է `appwidget grantbind` թույլտվությունը (DiLink-ում համակարգային
 * պատուհան չկա)։ Widget-ի վրա ⋮՝ փոխել կամ հեռացնել։
 */
@Composable
fun DashboardWidgetSlot(
    slot: String,
    emptyHint: String,
    requestGrant: ((Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val awm = remember { AppWidgetManager.getInstance(context) }
    val host = remember { DashboardWidgets.host(context) }
    var widgetId by remember { mutableIntStateOf(DashboardWidgets.get(context, slot)) }
    var pendingId by remember { mutableIntStateOf(-1) }
    var showPicker by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(host) {
        runCatching { host.startListening() }
        onDispose { runCatching { host.stopListening() } }
    }

    fun finishAdd(id: Int) {
        DashboardWidgets.clear(context, slot)
        DashboardWidgets.set(context, slot, id)
        widgetId = id
        pendingId = -1
    }

    fun abort(id: Int, text: String? = null) {
        runCatching { host.deleteAppWidgetId(id) }
        pendingId = -1
        if (text != null) message = text
    }

    val configureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingId
        if (id == -1) return@rememberLauncherForActivityResult
        if (r.resultCode == Activity.RESULT_OK) finishAdd(id) else abort(id)
    }

    val configureOrFinish: (Int) -> Unit = { id ->
        val info = awm.getAppWidgetInfo(id)
        if (info?.configure == null) {
            finishAdd(id)
        } else {
            try {
                configureLauncher.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(info.configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                )
            } catch (e: Exception) {
                // Կարգավորման էկրանը չբացվեց․ widget-ների մեծ մասը աշխատում է նաև առանց դրա
                finishAdd(id)
            }
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingId
        if (id == -1) return@rememberLauncherForActivityResult
        if (r.resultCode == Activity.RESULT_OK) configureOrFinish(id) else abort(id)
    }

    val noPermissionText = stringResource(
        R.string.kom_widget_no_permission,
        "adb shell appwidget grantbind --package ${context.packageName}",
    )

    fun bind(p: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        val tryBind = { runCatching { awm.bindAppWidgetIdIfAllowed(id, p.provider) }.getOrDefault(false) }
        if (tryBind()) return configureOrFinish(id)
        requestGrant { granted ->
            if (granted && tryBind()) {
                configureOrFinish(id)
            } else {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider)
                val started = intent.resolveActivity(context.packageManager) != null &&
                    runCatching { bindLauncher.launch(intent) }.isSuccess
                if (!started) abort(id, noPermissionText)
            }
        }
    }

    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(CardSurface)
            .border(1.dp, CardBorder, shape)
    ) {
        val info = if (widgetId != -1) awm.getAppWidgetInfo(widgetId) else null
        if (info != null) {
            key(widgetId) {
                var hostView by remember { mutableStateOf<AppWidgetHostView?>(null) }
                AndroidView(
                    factory = { host.createView(context.applicationContext, widgetId, info).also { hostView = it } },
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { px ->
                            val w = with(density) { px.width.toDp().value.toInt() }
                            val h = with(density) { px.height.toDp().value.toInt() }
                            runCatching { hostView?.updateAppWidgetSize(null, w, h, w, h) }
                        },
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(NavyDark.copy(alpha = 0.6f))
                    .clickable { showMenu = true },
                contentAlignment = Alignment.Center,
            ) { Text("⋮", color = TextSecondary, fontSize = 18.sp) }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().clickable { showPicker = true },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("＋", color = AccentGreen, fontSize = 30.sp)
                Text(emptyHint, color = TextSecondary, fontSize = 14.sp)
            }
        }
    }

    if (showPicker) {
        val pm = context.packageManager
        val providers = remember {
            awm.installedProviders.sortedBy { it.loadLabel(pm).toString().lowercase() }
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.kom_widget_pick_title), color = TextPrimary) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(providers) { p ->
                        val app = runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(p.provider.packageName, 0)).toString()
                        }.getOrDefault(p.provider.packageName)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPicker = false; bind(p) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(p.loadLabel(pm).toString(), color = TextPrimary, fontSize = 16.sp)
                            Text(app, color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.kom_cancel)) } },
            containerColor = CardSurface,
        )
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text(stringResource(R.string.kom_widget_menu_title), color = TextPrimary) },
            text = {
                Column {
                    Text(stringResource(R.string.kom_widget_change), color = AccentGreen, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable { showMenu = false; showPicker = true }.padding(vertical = 12.dp))
                    Text(stringResource(R.string.kom_widget_remove), color = TextSecondary, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            showMenu = false; DashboardWidgets.clear(context, slot); widgetId = -1
                        }.padding(vertical = 12.dp))
                }
            },
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text(stringResource(R.string.kom_cancel)) } },
            containerColor = CardSurface,
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text, color = TextPrimary) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            containerColor = CardSurface,
        )
    }
}
