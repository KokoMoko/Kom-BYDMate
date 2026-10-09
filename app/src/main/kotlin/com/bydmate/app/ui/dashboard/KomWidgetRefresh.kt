package com.bydmate.app.ui.dashboard

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kom-BYDMate: մեքենան միացնելիս վիջեթների հավելվածներին (օր․ AccuWeather) խնդրում է թարմացնել
 * իրենց վիջեթները, ինչպես launcher-ն է անում։ Անջատված մեքենայից հետո վիջեթի հավելվածի սեփական
 * թարմացումը կարող է երկար հետաձգվել (էկրանը քնած էր, չբացվող հավելվածը՝ ցածր standby bucket-ում),
 * և եղանակը ցույց էր տալիս հին տվյալներ, մինչև վիջեթին չսեղմեիր։ Ստանդարտ APPWIDGET_UPDATE-ը
 * ուղարկվում է երկու անգամ՝ ինտերնետի միանալուց հետո։
 */
object KomWidgetRefresh {
    private const val TAG = "KomWidgetRefresh"

    fun afterSwitchOn(ctx: Context, scope: CoroutineScope) {
        val app = ctx.applicationContext
        scope.launch {
            delay(15_000L); poke(app)
            delay(45_000L); poke(app)
        }
    }

    fun poke(ctx: Context) {
        val awm = AppWidgetManager.getInstance(ctx)
        val ids = runCatching { AppWidgetHost(ctx, DashboardWidgets.HOST_ID).appWidgetIds.toList() }.getOrDefault(emptyList())
        ids.mapNotNull { id -> awm.getAppWidgetInfo(id)?.provider?.let { it to id } }
            .groupBy({ it.first }, { it.second })
            .forEach { (provider, list) ->
                runCatching {
                    ctx.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, list.toIntArray()))
                }.onSuccess { Log.i(TAG, "update -> ${provider.flattenToShortString()} ${list.size}") }
                    .onFailure { Log.w(TAG, "update ${provider.flattenToShortString()}: ${it.message}") }
            }
    }
}
