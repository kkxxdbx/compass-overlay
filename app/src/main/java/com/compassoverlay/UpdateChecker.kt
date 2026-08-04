package com.compassoverlay

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private var checkedThisRun = false

    fun check(activity: Activity) {
        if (checkedThisRun) return
        checkedThisRun = true
        Thread {
            try {
                val conn = URL(BuildConfig.UPDATE_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(text)
                val remoteCode = json.getInt("versionCode")
                if (remoteCode <= BuildConfig.VERSION_CODE) return@Thread
                val force = json.optBoolean("force", false)
                val remoteName = json.optString("versionName", "")
                val downloadUrl = json.optString("url", BuildConfig.UPDATE_URL)
                activity.runOnUiThread {
                    showDialog(activity, remoteName, force, downloadUrl)
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun showDialog(activity: Activity, remoteName: String, force: Boolean, downloadUrl: String) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("发现新版本")
            .setMessage("当前版本 ${BuildConfig.VERSION_NAME}，新版本 ${remoteName} 已发布，建议更新。")
            .setCancelable(!force)
            .setNegativeButton(if (force) "退出" else "暂不更新") { d, _ -> d.dismiss() }
            .setPositiveButton("立即更新") { _, _ -> openDownload(activity, downloadUrl) }
            .create()
        dialog.setOnDismissListener { if (force && !dialog.isShowing) activity.finish() }
        dialog.show()
    }

    private fun openDownload(activity: Activity, downloadUrl: String) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
            )
        } catch (_: ActivityNotFoundException) {
        }
    }
}
