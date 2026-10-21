package com.java.myapplication

import android.net.Uri

/** 基于域名黑名单的轻量广告/追踪拦截 */
object AdBlocker {
    private val blockedHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "adservice.google.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "ads.yahoo.com", "advertising.com", "adnxs.com", "adsrvr.org",
        "amazon-adsystem.com", "taboola.com", "outbrain.com", "criteo.com",
        "criteo.net", "scorecardresearch.com", "quantserve.com",
        "adskeeper.com", "mgid.com", "popads.net", "popcash.net",
        "exoclick.com", "juicyads.com", "adcolony.com", "applovin.com",
        "unityads.unity3d.com", "inmobi.com", "mopub.com", "ironsrc.com",
        "vungle.com", "chartboost.com", "appnext.com", "startappservice.com",
        "umeng.com", "umeng.co", "cnzz.com", "51.la",
        "admaster.com.cn", "miaozhen.com", "gridsumdissector.com",
        "doubleclick.com.cn", "tanx.com", "alimama.com", "mmstat.com",
        "facebook.net", "connect.facebook.net", "ads.twitter.com",
        "ads-tiktok.com", "analytics.tiktok.com", "hotjar.com",
        "clarity.ms", "mouseflow.com", "fullstory.com"
    )

    private val blockedSuffixes = listOf(".doubleclick.net", ".googlesyndication.com")

    fun isBlocked(url: String?): Boolean {
        if (!Settings.adBlock || url == null) return false
        val host = Uri.parse(url).host?.lowercase() ?: return false
        if (host in blockedHosts) return true
        for (h in blockedHosts) if (host.endsWith(".$h")) return true
        for (s in blockedSuffixes) if (host.endsWith(s)) return true
        return false
    }
}