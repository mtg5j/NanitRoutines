package com.droid.nanitroutines.data.lights

import android.content.Context
import android.content.Intent
import android.net.Uri

class BabyDaybookHelper(
    private val context: Context
) : BabyDaybookLauncher {

    companion object {
        const val BABY_DAYBOOK_PACKAGE = "com.drillyapps.babydaybook"
        const val NANIT_PACKAGE = "com.nanit.baby"
    }

    override fun isBabyDaybookInstalled(): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(BABY_DAYBOOK_PACKAGE) != null
        } catch (_: Exception) {
            false
        }
    }

    override fun openBabyDaybookSleepPage(): Result<String> {
        return runCatching {
            val pm = context.packageManager

            // 1. Try direct deep-link to Baby Daybook Sleep page first
            val sleepDeepLink = Intent(Intent.ACTION_VIEW, Uri.parse("babydaybook://sleep")).apply {
                setPackage(BABY_DAYBOOK_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (sleepDeepLink.resolveActivity(pm) != null) {
                context.startActivity(sleepDeepLink)
                return@runCatching "Opened Baby Daybook (Sleep)"
            }

            // 2. Try generic babydaybook://sleep URI without package lock
            val genericDeepLink = Intent(Intent.ACTION_VIEW, Uri.parse("babydaybook://sleep")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (genericDeepLink.resolveActivity(pm) != null) {
                context.startActivity(genericDeepLink)
                return@runCatching "Opened Baby Daybook (Sleep)"
            }

            // 3. Fallback to launching the Baby Daybook app directly
            val launchIntent = pm.getLaunchIntentForPackage(BABY_DAYBOOK_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return@runCatching "Opened Baby Daybook"
            }

            "Baby Daybook app not installed on this device"
        }
    }

    override fun openNanitLiveCamera(): Result<String> {
        return runCatching {
            val pm = context.packageManager

            // 1. Launch official Nanit app if installed
            val nanitLaunch = pm.getLaunchIntentForPackage(NANIT_PACKAGE)
            if (nanitLaunch != null) {
                nanitLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(nanitLaunch)
                return@runCatching "Opened Nanit Live Camera"
            }

            // 2. Fallback to Nanit Web Portal (https://my.nanit.com) or Play Store
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://my.nanit.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            "Opened Nanit Web Camera View"
        }
    }
}
