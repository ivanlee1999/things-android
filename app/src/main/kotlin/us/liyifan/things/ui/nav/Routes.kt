package us.liyifan.things.ui.nav

import kotlinx.serialization.Serializable

/**
 * Every screen, as a typed route.
 *
 * The three deep links are what the app shortcuts and the BOOX smart button point at:
 * things://today, things://new and things://task/<id>.
 */
@Serializable
sealed interface Route {

    /** First run, or after the server is forgotten. */
    @Serializable data object Connect : Route

    /** The list of lists, which is the phone's home screen. */
    @Serializable data object Home : Route

    @Serializable data object Inbox : Route
    @Serializable data object Today : Route
    @Serializable data object Upcoming : Route
    @Serializable data object Anytime : Route
    @Serializable data object Someday : Route
    @Serializable data object Logbook : Route
    @Serializable data object Trash : Route
    @Serializable data object Settings : Route

    /** [isNew] selects the title so typing replaces "New Project". */
    @Serializable data class Project(val id: String, val isNew: Boolean = false) : Route

    @Serializable data class Area(val id: String, val isNew: Boolean = false) : Route

    @Serializable data class Search(val q: String = "") : Route
}

/** The label on the back chevron, which is where the user came from, not where they are. */
const val BACK_LABEL = "Lists"

object DeepLinks {
    const val SCHEME = "things"
    const val TODAY = "$SCHEME://today"
    const val NEW = "$SCHEME://new"
    const val TASK_PREFIX = "$SCHEME://task/"
}
