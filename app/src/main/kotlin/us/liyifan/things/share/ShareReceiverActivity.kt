package us.liyifan.things.share

import android.app.Activity
import android.os.Bundle

/** Receives ACTION_SEND text and files it in the Inbox. Wired up once the repository exists. */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
