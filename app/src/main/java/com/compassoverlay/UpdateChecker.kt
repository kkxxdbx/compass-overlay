package com.compassoverlay

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private var checkedThisRun = false

    fun check(activity: Activity) {
        if (checkedThisRun) return
        checkedThisRun = true
        // 用弱引用持有 Activity：网络线程返回时 Activity 可能已被销毁，
        // 直接弹窗会抛 BadTokenException 导致崩溃
        val ref = WeakReference(activity)
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
                val act = ref.get() ?: return@Thread
                if (act.isFinishing || act.isDestroyed) return@Thread
                act.runOnUiThread {
                    // 弹窗前再次确认 Activity 仍存活，避免对已销毁窗口弹窗
                    if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                    try {
                        showDialog(act, remoteName, force, downloadUrl)
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun showDialog(activity: Activity, remoteName: String, force: Boolean, downloadUrl: String) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_title))
            .setMessage(activity.getString(R.string.update_message, BuildConfig.VERSION_NAME, remoteName))
            .setCancelable(!force)
            .setNegativeButton(
                if (force) activity.getString(R.string.update_exit) else activity.getString(R.string.update_later)
            ) { d, _ -> d.dismiss() }
            .setPositiveButton(activity.getString(R.string.update_now)) { _, _ -> openDownload(activity, downloadUrl) }
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
