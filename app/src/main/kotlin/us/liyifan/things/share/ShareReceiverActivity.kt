package us.liyifan.things.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.launch
import us.liyifan.things.ThingsApp
import us.liyifan.things.model.NewTaskInit

/**
 * "Share to Things": anything sent as text becomes a to-do in the Inbox.
 *
 * It goes through the same queue as everything else, so sharing a link with no signal still
 * works — the to-do is there, and the server hears about it later.
 */
class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ThingsApp).container

        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()

        if (subject.isEmpty() && text.isEmpty()) {
            finish()
            return
        }

        // A shared page has a title and a URL; the title is the to-do and the URL is the note.
        // A bare string is the to-do itself.
        val init = if (subject.isNotEmpty()) {
            NewTaskInit(title = subject, note = text.ifEmpty { null })
        } else {
            NewTaskInit(title = text)
        }

        container.scope.launch { container.repository.createTask(init) }
        Toast.makeText(this, "Added to Inbox", Toast.LENGTH_SHORT).show()
        finish()
    }
}
