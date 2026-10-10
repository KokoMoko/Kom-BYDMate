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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
    const val SLOT_PHONE = "phone"  // ձախ՝ կոմպակտ տողի տակ (օր․ Bluetooth Phone)
    const val SLOT_PHONE2 = "phone2"  // Phone-ի կողքին՝ օգտատիրոջ ընտրությամբ
    private const val PREFS = "kom_dashboard_widgets"

    /**
     * Widget-ի կարգավորման էկրանը բացվում է AppWidgetHost-ի պաշտոնական API-ով (AccuWeather-ի նման
     * widget-ների configure activity-ն exported չէ)․ արդյունքը գալիս է MainActivity.onActivityResult-ով։
     */
    const val REQ_CONFIGURE = 7301
    val configureResults = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 4)

    @Volatile private var host: AppWidgetHost? = null

    private var listeners = 0

    @Synchronized
    fun acquireListening(ctx: Context) {
        if (listeners++ == 0) runCatching { host(ctx).startListening() }
    }

    @Synchronized
    fun releaseListening(ctx: Context) {
        listeners = (listeners - 1).coerceAtLeast(0)
        if (listeners == 0) runCatching { host(ctx).stopListening() }
    }

    fun host(ctx: Context): AppWidgetHost =
        host ?: synchronized(this) {
            host ?: object : AppWidgetHost(ctx.applicationContext, HOST_ID) {
                // Ախտորոշում՝ որ widget id-ին ինչ layout է գալիս (widget-ների «տեղափոխվելու» խնդրի համար)
                override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
                    object : AppWidgetHostView(context) {
                        override fun updateAppWidget(remoteViews: android.widget.RemoteViews?) {
                            android.util.Log.i("KomWidget", "update id=$appWidgetId view=${System.identityHashCode(this)} " +
                                "layout=${remoteViews?.layoutId?.let { runCatching { context.packageManager
                                    .getResourcesForApplication(remoteViews.`package`).getResourceEntryName(it) }.getOrNull() }}")
                            super.updateAppWidget(remoteViews)
                        }
                    }
            }.also { host = it }
        }

    /**
     * Էջի սեփական սլոտ՝ «էջ:հիմք» (օր․ «cluster:right»)։ Ամեն էջ իր widget-ն ու չափն ունի․
     * քանի դեռ էջի սլոտում ոչինչ չի ընտրվել, այն ժառանգում է հիմքի (Classic-ի) widget-ը և չափը։
     */
    fun scoped(page: String, slot: String): String = "$page:$slot"
    private fun parent(slot: String): String? = slot.substringAfter(':', "").ifEmpty { null }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(ctx: Context, slot: String): Int {
        val p = prefs(ctx)
        val parentSlot = parent(slot)
        // Էջի սլոտը առաջին անգամ՝ «պատճենում» ենք հիմքի widget-ը և չափը, այդ պահից այն անկախ է
        // (առաջ ժառանգումը մշտական էր, և Classic-ում փոխելիս այս էջի widget-ն էլ «ինքն իրեն» փոխվում էր)
        if (parentSlot != null && !p.contains(slot)) {
            val inherited = get(ctx, parentSlot)
            p.edit()
                .putInt(slot, if (inherited > 0 || inherited == BUILTIN_TRIP) inherited else EMPTY)
                .putInt("${slot}_scale", getScale(ctx, parentSlot))
                .commit()
        }
        val own = p.getInt(slot, -1)
        return if (own == EMPTY) -1 else own
    }

    fun set(ctx: Context, slot: String, id: Int) =
        prefs(ctx).edit().putInt(slot, id).apply()

    /** Widget-ի մասշտաբը տոկոսներով (100 = գոտու իրական չափը)։ Էջինը չկա՝ ժառանգում է հիմքից։ */
    fun getScale(ctx: Context, slot: String): Int {
        val p = prefs(ctx)
        if (p.contains("${slot}_scale")) return p.getInt("${slot}_scale", 100)
        return parent(slot)?.let { getScale(ctx, it) } ?: 100
    }

    fun setScale(ctx: Context, slot: String, pct: Int) =
        prefs(ctx).edit().putInt("${slot}_scale", pct).apply()

    /** Հեռացնում է սլոտի widget-ը․ widget id-ն ջնջվում է միայն եթե այլ սլոտ (էջ) այն չի օգտագործում։ */
    fun clear(ctx: Context, slot: String) {
        val id = prefs(ctx).getInt(slot, -1)
        // Էջի սլոտը նշում ենք «դատարկ» (որ նորից չժառանգի), հիմքինը՝ ուղղակի հեռացնում
        if (parent(slot) != null) prefs(ctx).edit().putInt(slot, EMPTY).apply()
        else prefs(ctx).edit().remove(slot).apply()
        if (id > 0 && !isUsedElsewhere(ctx, id)) runCatching { host(ctx).deleteAppWidgetId(id) }
    }

    private const val EMPTY = -2

    /** Our own «TRIP 1 / TRIP 2» card in place of an Android widget (stored as the slot's id). */
    const val BUILTIN_TRIP = -10

    private fun isUsedElsewhere(ctx: Context, id: Int): Boolean =
        prefs(ctx).all.any { (k, v) -> v == id && !k.endsWith("_scale") && !k.startsWith("speedo_") }
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
    suggestion: String? = null,
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
    // Սպասում ենք host API-ով բացված կարգավորման արդյունքին․ նոր widget (pendingId) կամ վերակարգավորում
    var awaitingHostConfig by remember { mutableStateOf(false) }
    var reconfigId by remember { mutableIntStateOf(-1) }
    var viewKey by remember { mutableIntStateOf(0) }
    var scalePct by remember { mutableIntStateOf(DashboardWidgets.getScale(context, slot)) }
    var showSize by remember { mutableStateOf(false) }

    DisposableEffect(host) {
        // Host-ը ընդհանուր է բոլոր էջերի համար․ լսումը անջատվում է միայն երբ ոչ մի սլոտ չի մնացել
        // (առաջ էջը փոխելիս հին էջը անջատում էր լսումը բոլորի համար, և widget-ները չէին թարմանում)
        DashboardWidgets.acquireListening(context)
        onDispose { DashboardWidgets.releaseListening(context) }
    }

    fun finishAdd(id: Int) {
        android.util.Log.i("KomWidget", "finishAdd slot=$slot id=$id")
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

    LaunchedEffect(Unit) {
        DashboardWidgets.configureResults.collect { rc ->
            val id = pendingId
            if (awaitingHostConfig && id != -1) {
                awaitingHostConfig = false
                if (rc == Activity.RESULT_OK) finishAdd(id) else abort(id)
            } else if (reconfigId != -1) {
                reconfigId = -1
                viewKey++  // նոր view՝ թարմ կարգավորումով
            }
        }
    }

    /** Բացում է widget-ի կարգավորումը պաշտոնական API-ով․ false, եթե չստացվեց։ */
    fun startHostConfigure(id: Int): Boolean {
        val activity = context.findActivity() ?: return false
        return runCatching {
            host.startAppWidgetConfigureActivityForResult(activity, id, 0, DashboardWidgets.REQ_CONFIGURE, null)
        }.isSuccess
    }

    val configureOrFinish: (Int) -> Unit = { id ->
        val info = awm.getAppWidgetInfo(id)
        if (info?.configure == null) {
            finishAdd(id)
        } else if (startHostConfigure(id)) {
            awaitingHostConfig = true
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
        val info = if (widgetId > 0) awm.getAppWidgetInfo(widgetId) else null
        if (widgetId == DashboardWidgets.BUILTIN_TRIP) {
            com.bydmate.app.ui.reports.TripCountersCard(Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(NavyDark.copy(alpha = 0.6f))
                    .clickable { showMenu = true },
                contentAlignment = Alignment.Center,
            ) { Text("⋮", color = TextSecondary, fontSize = 15.sp) }
        } else if (info != null) {
            key(widgetId, viewKey) {
                // Մասշտաբ․ widget-ին տալիս ենք «վիրտուալ» չափ (գոտի / s) և View-ն փոքրացնում/մեծացնում ենք s-ով։
                // Փոքր s → widget-ը կարծում է, որ տեղը մեծ է, և ցույց է տալիս ավելի շատ բովանդակություն։
                // Scale-ը դրված է հենց View-ի վրա, որ Android-ը ճիշտ փոխակերպի հպումները։
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val s = scalePct / 100f
                    val vwPx = with(density) { (maxWidth.toPx() / s).toInt() }
                    val vhPx = with(density) { (maxHeight.toPx() / s).toInt() }
                    val vwDp = with(density) { vwPx.toDp().value.toInt() }
                    val vhDp = with(density) { vhPx.toDp().value.toInt() }
                    AndroidView(
                        factory = { ctx ->
                            android.widget.FrameLayout(ctx).apply {
                                clipChildren = true
                                val hv = host.createView(context.applicationContext, widgetId, info)
                                android.util.Log.i("KomWidget", "createView slot=$slot id=$widgetId view=${System.identityHashCode(hv)}")
                                addView(hv)
                                centerWidgetContentVertically(hv)
                            }
                        },
                        update = { frame ->
                            var hv = frame.getChildAt(0) as? AppWidgetHostView ?: return@AndroidView
                            // Ապահովություն՝ View-ն պետք է ցույց տա հենց այս սլոտի widget-ը
                            if (hv.appWidgetId != widgetId) {
                                frame.removeAllViews()
                                hv = host.createView(context.applicationContext, widgetId, info)
                                frame.addView(hv)
                                centerWidgetContentVertically(hv)
                            }
                            hv.layoutParams = android.widget.FrameLayout.LayoutParams(vwPx, vhPx)
                            hv.pivotX = 0f
                            hv.pivotY = 0f
                            hv.scaleX = s
                            hv.scaleY = s
                            // Չափը հայտնում ենք միայն 1.5 վրկ կայուն մնալուց հետո և միայն փոխվելու դեպքում։
                            // AccuWeather-ը (Glance) դասավորությունը ընտրում է այս չափով և վերագծվում է միայն
                            // հաջորդ անգամ գործարկվելիս (մեքենան հետին պլանում արգելափակում է չափի փոփոխության
                            // ազդանշանը), ուստի անցողիկ չափը (էջի փոխում, անիմացիա) որոշում էր դասավորությունը՝
                            // երբեմն Daily (7 օր) տեսքը։
                            val reportHv = hv
                            (frame.getTag(R.id.kom_widget_size_job) as? Runnable)?.let { frame.removeCallbacks(it) }
                            val job = Runnable {
                                val p = frame.context.getSharedPreferences("kom_widget_sizes", android.content.Context.MODE_PRIVATE)
                                val key = "w_${reportHv.appWidgetId}"
                                val now = "${vwDp}x$vhDp"
                                if (p.getString(key, null) != now) {
                                    runCatching { reportHv.updateAppWidgetSize(null, vwDp, vhDp, vwDp, vhDp) }
                                    p.edit().putString(key, now).apply()
                                }
                            }
                            frame.setTag(R.id.kom_widget_size_job, job)
                            frame.postDelayed(job, 1_500L)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
                // նոր օգտատիրոջ համար՝ ինչ կարելի է դնել այստեղ (հեղինակի տարբերակի օրինակով)
                if (suggestion != null) Text(suggestion, color = TextMuted, fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
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
                    item {
                        // our own card first: TRIP 1 / TRIP 2, details and reset after charging on tap
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPicker = false; finishAdd(DashboardWidgets.BUILTIN_TRIP) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(stringResource(R.string.kom_widget_trip), color = AccentGreen, fontSize = 16.sp)
                            Text(stringResource(R.string.kom_widget_trip_desc), color = TextMuted, fontSize = 12.sp)
                        }
                    }
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
                    val builtIn = widgetId == DashboardWidgets.BUILTIN_TRIP
                    if (!builtIn && awm.getAppWidgetInfo(widgetId)?.configure != null) {
                        Text(stringResource(R.string.kom_widget_configure), color = AccentGreen, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                showMenu = false
                                if (startHostConfigure(widgetId)) reconfigId = widgetId
                            }.padding(vertical = 12.dp))
                    }
                    if (!builtIn) Text(stringResource(R.string.kom_widget_size), color = AccentGreen, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable { showMenu = false; showSize = true }
                            .padding(vertical = 12.dp))
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

    if (showSize) {
        AlertDialog(
            onDismissRequest = { showSize = false },
            title = { Text(stringResource(R.string.kom_widget_size), color = TextPrimary) },
            text = {
                Column {
                    Text("$scalePct%", color = AccentGreen, fontSize = 22.sp)
                    // Փոփոխությունը երևում է անմիջապես՝ widget-ի վրա
                    Slider(
                        value = scalePct.toFloat(),
                        onValueChange = { scalePct = (it / 5).toInt() * 5 },
                        onValueChangeFinished = { DashboardWidgets.setScale(context, slot, scalePct) },
                        valueRange = 60f..150f,
                    )
                    Text(stringResource(R.string.kom_widget_size_hint), color = TextMuted, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { DashboardWidgets.setScale(context, slot, scalePct); showSize = false }) {
                    Text(stringResource(R.string.kom_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { scalePct = 100; DashboardWidgets.setScale(context, slot, 100) }) { Text("100%") }
            },
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Widget-ների մեծ մասը իր բովանդակությունը դնում է վերևում, և շրջանակում ներքևից մեծ դատարկ տեղ
 * էր մնում։ Ամեն layout-ից հետո գտնում ենք իրական բովանդակության սահմանները (առանց ամբողջ
 * բարձրությամբ կոնտեյներների) և hv-ն տեղաշարժում ենք այնպես, որ վերևից և ներքևից բացատները հավասար լինեն։
 */
private fun centerWidgetContentVertically(hv: AppWidgetHostView) {
    val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        val h = hv.height
        if (h <= 0) return@OnGlobalLayoutListener
        var top = Int.MAX_VALUE
        var bottom = Int.MIN_VALUE
        fun walk(v: android.view.View, offsetY: Int) {
            if (v.visibility != android.view.View.VISIBLE || v.height <= 0) return
            val y = offsetY + v.top
            // Գրեթե ամբողջ բարձրությամբ կոնտեյները «լցնող» է․ նայում ենք ներսը
            if (v is android.view.ViewGroup && v.height >= h * 0.95f && v.childCount > 0) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i), y)
            } else {
                top = minOf(top, y)
                bottom = maxOf(bottom, y + v.height)
            }
        }
        for (i in 0 until hv.childCount) walk(hv.getChildAt(i), 0)
        val dy = if (top == Int.MAX_VALUE) 0f else ((h - bottom) - top) / 2f
        // hv-ի ներսի px-ները scale-ից առաջ են, translationY-ը՝ scale-ից հետո
        val t = (dy * hv.scaleY).coerceAtLeast(0f)
        if (kotlin.math.abs(hv.translationY - t) > 0.5f) hv.translationY = t
    }
    hv.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: android.view.View) { v.viewTreeObserver.addOnGlobalLayoutListener(listener) }
        override fun onViewDetachedFromWindow(v: android.view.View) { v.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    })
}
