package com.bydmate.app.ui.dashboard

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay

/**
 * Kom-BYDMate: վիջեթներ, որոնց հավելվածը թարմացնում է վիջեթը միայն ինքը բացվելիս։
 *
 * AccuWeather-ի վիջեթը (Glance + WorkManager) մեքենան անջատված մնալուց հետո ցույց էր տալիս հին
 * եղանակը, մինչև վիջեթին չսեղմեիր։ Փորձված է մեքենայի վրա (2026-10-10). ստանդարտ APPWIDGET_UPDATE-ը
 * միայն համակարգը կարող է ուղարկել (Permission Denial), իսկ նրա job-երի ստիպողական գործարկումը
 * WorkManager-ը հետաձգում է («executed before schedule»)։ Աշխատում է միայն հավելվածի բացումը,
 * ուստի KomAutostart-ը, ինտերնետի միանալուց հետո, այն բացում է մի պահ և վերադարձնում Kom-ը։
 */
object KomWidgetRefresh {
    /** Հավելվածներ, որոնց վիջեթը թարմանում է միայն հավելվածը բացելիս։ */
    private val REFRESH_ON_OPEN = setOf("com.accuweather.android")

    /** Գլխավոր էջի վիջեթների հավելվածներից նրանք, որոնց պետք է բացել։ */
    fun packagesToOpen(ctx: Context): List<String> {
        val awm = AppWidgetManager.getInstance(ctx)
        val ids = runCatching { AppWidgetHost(ctx, DashboardWidgets.HOST_ID).appWidgetIds.toList() }.getOrDefault(emptyList())
        return ids.mapNotNull { awm.getAppWidgetInfo(it)?.provider?.packageName }.distinct().filter { it in REFRESH_ON_OPEN }
    }

    /** Սպասում է ինտերնետին (առավելագույնը [maxMs]), որ բացված հավելվածը նոր տվյալներ ստանա։ */
    suspend fun awaitInternet(ctx: Context, maxMs: Long = 60_000L): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        var waited = 0L
        while (waited < maxMs) {
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) return true
            delay(2_000L); waited += 2_000L
        }
        return false
    }
}
