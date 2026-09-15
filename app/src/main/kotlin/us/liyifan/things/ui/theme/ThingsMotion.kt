package us.liyifan.things.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset

/**
 * Every animation in the app comes from here, which is what makes "no animation on e-ink" a rule
 * with one enforcement point rather than a hundred call sites to remember.
 *
 * On e-paper an animation is not slow, it is wrong: each frame is a partial refresh, so a 200 ms
 * fade becomes a sequence of grey ghosts that settle long after the gesture ended. Snapping
 * straight to the final state is both faster and the only thing that looks deliberate.
 */
class ThingsMotion(private val eink: Boolean) {

    fun <T> spring(
        dampingRatio: Float = Spring.DampingRatioNoBouncy,
        stiffness: Float = Spring.StiffnessMedium,
    ): FiniteAnimationSpec<T> =
        if (eink) snap() else spring(dampingRatio = dampingRatio, stiffness = stiffness)

    fun <T> tween(durationMillis: Int = 220): FiniteAnimationSpec<T> =
        if (eink) snap() else tween(durationMillis)

    fun <T> value(durationMillis: Int = 220): AnimationSpec<T> = tween(durationMillis)

    /** The tick that scales in when a checkbox is filled. */
    fun checkSpring(): FiniteAnimationSpec<Float> =
        if (eink) snap() else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessHigh)

    // Navigation. A pushed page slides in from the right, as it does on the phone layout of the
    // web client; on e-ink it simply appears.
    fun enter(): EnterTransition = if (eink) EnterTransition.None else {
        slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(260)) +
            fadeIn(animationSpec = tween(180))
    }

    fun exit(): ExitTransition = if (eink) ExitTransition.None else {
        slideOutHorizontally(targetOffsetX = { -it / 6 }, animationSpec = tween(260)) +
            fadeOut(animationSpec = tween(180))
    }

    fun popEnter(): EnterTransition = if (eink) EnterTransition.None else {
        slideInHorizontally(initialOffsetX = { -it / 6 }, animationSpec = tween(260)) +
            fadeIn(animationSpec = tween(180))
    }

    fun popExit(): ExitTransition = if (eink) ExitTransition.None else {
        slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(260)) +
            fadeOut(animationSpec = tween(180))
    }

    fun fadeIn(): EnterTransition = if (eink) EnterTransition.None else fadeIn(animationSpec = tween(160))
    fun fadeOut(): ExitTransition = if (eink) ExitTransition.None else fadeOut(animationSpec = tween(160))

    /** Rows appearing and leaving a list. */
    fun itemPlacement(): FiniteAnimationSpec<IntOffset> = if (eink) snap() else spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    companion object {
        /**
         * How long a ticked to-do stays on screen before it leaves, copied from the web client
         * and from Things itself. The pause is the point: it confirms the tap landed on the row
         * the user meant, and it is kept on e-ink where the feedback matters more, not less.
         */
        const val SETTLE_MS = 1400L
    }
}
