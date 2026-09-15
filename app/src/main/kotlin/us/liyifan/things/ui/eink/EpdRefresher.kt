package us.liyifan.things.ui.eink

import android.os.Build
import android.util.Log
import android.view.View

/**
 * Asks a BOOX panel for a full repaint.
 *
 * Partial refreshes are what make e-ink feel quick, and they are also what leaves the ghost of
 * the last screen behind. One full repaint after navigating clears it. The whole thing is
 * optional: on any other device there is nothing to call.
 *
 * Reached by reflection rather than by linking Onyx's SDK. Their Maven is plain HTTP with no
 * mirror, and a CI build should not depend on it for one call that does not exist on the
 * hardware most of these builds will never run on.
 */
interface EpdRefresher {
    fun fullRefresh(view: View)

    companion object {
        fun forThisDevice(): EpdRefresher =
            if (Build.MANUFACTURER.equals("ONYX", ignoreCase = true)) OnyxEpdRefresher() else NoopEpdRefresher
    }
}

object NoopEpdRefresher : EpdRefresher {
    override fun fullRefresh(view: View) = Unit
}

private class OnyxEpdRefresher : EpdRefresher {

    private val controller: Class<*>? by lazy {
        runCatching { Class.forName("com.onyx.android.sdk.api.device.epd.EpdController") }.getOrNull()
    }

    private val updateMode: Class<*>? by lazy {
        runCatching { Class.forName("com.onyx.android.sdk.api.device.epd.UpdateMode") }.getOrNull()
    }

    override fun fullRefresh(view: View) {
        val epd = controller ?: return
        val mode = updateMode ?: return
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val gc = java.lang.Enum.valueOf(mode as Class<out Enum<*>>, "GC")
            epd.getMethod("invalidate", View::class.java, mode).invoke(null, view, gc)
        }.onFailure {
            // The SDK moves between firmware versions; a missing method is not worth a crash on
            // a device that otherwise works perfectly with a little ghosting.
            Log.d(TAG, "full refresh unavailable: ${it.message}")
        }
    }

    private companion object {
        const val TAG = "EpdRefresher"
    }
}
