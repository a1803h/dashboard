package tj.umed.drivedashboard

import android.content.Context

/** One trip controller shared by the UI and the foreground location service in this process. */
object TripRuntime {
    @Volatile private var controller: DashboardController? = null

    @Synchronized
    fun get(context: Context): DashboardController {
        return controller ?: DashboardController(context.applicationContext).also { controller = it }
    }
}
