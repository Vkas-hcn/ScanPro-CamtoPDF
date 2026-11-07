package com.scanpro.cam.pdf.keepalive

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import com.google.firebase.messaging.FirebaseMessaging

/** Call before starting any replacement background work, on every app startup until successful. */
object LegacyBackgroundCleanup {
    private const val PREFS = "legacy_background_migration_v2"
    private const val LOCAL_DONE = "local_done"
    private const val TOPIC_DONE = "topic_done"
    private const val OLD_TOPIC = "scanpro_pdf"
    private var unsubscribeInFlight = false

    data class Result(val localComplete: Boolean, val topicComplete: Boolean, val errors: List<String>)

    /** Local cleanup completes synchronously; topic removal continues asynchronously and is retried. */
    @Synchronized
    fun runBeforeNewWork(context: Context): Result {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val errors = mutableListOf<String>()
        var localComplete = prefs.getBoolean(LOCAL_DONE, false)
        if (!localComplete) {
            fun attempt(name: String, action: () -> Unit) {
                try {
                    action()
                } catch (e: Exception) {
                    errors.add("$name: ${e.javaClass.simpleName}")
                }
            }
            // Invalidate the old notification dismissal callback before a service stop or cancel.
            // Otherwise the old ongoing notification may immediately be posted again.
            var oldDeleteIntentCleared = false
            attempt("legacy widget delete intent") {
                val intent = Intent("com.myapp.action.show_ntf")
                    .setClassName(app, "com.zppko.pdfplus.nor.horus.WidgetPinnedReceiver")
                PendingIntent.getBroadcast(
                    app, 1001, intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )?.cancel()
                oldDeleteIntentCleared = true
            }
            for (component in listOf(
                "com.scanpro.cam.pdf.keepalive.SpApoForegroundService",
                "com.scanpro.cam.pdf.keepalive.SpYoutubeService",
                "androidx.core.app.ActionYouService",
                "com.zppko.pdfplus.nor.ss.CCPdfToolService",
                "com.zppko.pdfplus.nor.ads.TextService",
            )) {
                // Do not remove an A foreground notification while its callback is still live.
                if (!oldDeleteIntentCleared && component.startsWith("com.zppko.")) continue
                attempt("stop $component") {
                    app.stopService(Intent().setClassName(app, component))
                }
            }
            for ((id, components) in listOf(
                4321 to setOf(
                    "com.scanpro.cam.pdf.keepalive.SpResidentLaunchJob",
                    "com.zppko.pdfplus.nor.horus.AbcLaunceJob",
                ),
                54321 to setOf("com.scanpro.cam.pdf.keepalive.SpPersistJobService"),
                46555 to setOf("com.scanpro.cam.pdf.keepalive.SpAssistanceLaunchJobService"),
            )) {
                attempt("job $id") {
                    val scheduler = checkNotNull(app.getSystemService(JobScheduler::class.java))
                    val job = scheduler.getPendingJob(id)
                    // The A 4321 job must also be removed before its replacement is enqueued.
                    // Never cancel a reused ID belonging to a different component or an SDK.
                    if (job != null && job.service.packageName == app.packageName && job.service.className in components) {
                        scheduler.cancel(id)
                    }
                }
            }
            for ((requestCode, component) in listOf(
                41385 to "com.scanpro.cam.pdf.notice.SpAlarmReceiver",
                81337 to "com.scanpro.cam.pdf.notice.SpRetentionRecallReceiver",
            )) {
                attempt("alarm $requestCode") {
                    val intent = Intent().setClassName(app, component)
                    val pending = PendingIntent.getBroadcast(
                        app, requestCode, intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                    )
                    if (pending != null) {
                        checkNotNull(app.getSystemService(AlarmManager::class.java)).cancel(pending)
                        pending.cancel()
                    }
                }
            }
            // Cancel only known legacy IDs; keep notification channels and their user settings.
            val notificationIds = intArrayOf(
                234397, 234398, 945458, 698228,
                1345141, 1098992, 1965755, 2176732, 2984563, 2295638,
                3317982, 3016683, 3712931, 4849184, 4886674, 4714216,
                5138216, 5426203, 58786810, 6949829, 6126092, 6876659,
                7121207, 7352892, 7871372,
                4856, 8562, 70101,
            )
            for (id in notificationIds) {
                if (!oldDeleteIntentCleared && id in setOf(4856, 8562, 70101)) continue
                attempt("notification $id") {
                    checkNotNull(app.getSystemService(NotificationManager::class.java)).cancel(id)
                }
            }
            // Old A alarms used random request codes and cannot safely be enumerated here.
            // Their receiver must reject broadcasts without the new migration-version extra.
            if (errors.isEmpty()) {
                localComplete = prefs.edit().putBoolean(LOCAL_DONE, true).commit()
                if (!localComplete) errors.add("persist local cleanup state failed")
            }
        }
        val topicComplete = prefs.getBoolean(TOPIC_DONE, false)
        if (!topicComplete && !unsubscribeInFlight) {
            try {
                unsubscribeInFlight = true
                FirebaseMessaging.getInstance().unsubscribeFromTopic(OLD_TOPIC)
                    .addOnCompleteListener { task ->
                        synchronized(this) {
                            // Persist only a completed unsubscribe. A failed task or write leaves
                            // the marker unset so a later startup can retry independently of local cleanup.
                            runCatching {
                                if (task.isSuccessful) prefs.edit().putBoolean(TOPIC_DONE, true).commit()
                            }
                            unsubscribeInFlight = false
                        }
                    }
            } catch (e: Exception) {
                unsubscribeInFlight = false
                errors.add("topic unsubscribe: ${e.javaClass.simpleName}")
            }
        }
        return Result(localComplete, topicComplete, errors)
    }
}
