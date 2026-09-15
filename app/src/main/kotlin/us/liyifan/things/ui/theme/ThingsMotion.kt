package us.liyifan.things.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.snap as coreSnap
import androidx.compose.animation.core.spring as coreSpring
import androidx.compose.animation.core.tween as coreTween
import androidx.compose.animation.fadeIn as coreFadeIn
import androidx.compose.animation.fadeOut as coreFadeOut
import androidx.compose.animation.slideInHorizontally as coreSlideIn
import androidx.compose.animation.slideOutHorizontally as coreSlideOut

/**
 * Every animation in the app comes from here, which is what makes "no animation on e-ink" a rule
 * with one enforcement point rather than a hundred call sites to remember.
 *
 * On e-paper an animation is not merely slow, it is wrong: each frame is a partial refresh, so a
 * 200 ms fade becomes a run of grey ghosts that settle long after the gesture ended. Snapping
 * straight to the final state is both faster and the only thing that looks deliberate.
 *
 * The Compose builders are imported under their own names because these methods share theirs;
 * calling `spring(...)` inside `fun spring(...)` resolves to the method, not the builder, and
 * recurses until the stack runs out.
 */
class ThingsMotion(private val eink: Boolean) {

    fun <T> spring(
        dampingRatio: Float = Spring.DampingRatioNoBouncy,
        stiffness: Float = Spring.StiffnessMedium,
    ): FiniteAnimationSpec<T> =
        if (eink) coreSnap() else coreSpring(dampingRatio = dampingRatio, stiffness = stiffness)

    fun <T> tween(durationMillis: Int = 220): FiniteAnimationSpec<T> =
        if (eink) coreSnap() else coreTween(durationMillis)

    fun <T> value(durationMillis: Int = 220): AnimationSpec<T> = tween(durationMillis)

    /** The tick that scales in when a checkbox is filled. */
    fun checkSpring(): FiniteAnimationSpec<Float> =
        if (eink) coreSnap() else coreSpring(dampingRatio = 0.55f, stiffness = Spring.StiffnessHigh)

    // Navigation. A pushed page slides in from the right, as it does on the phone layout of the
    // web client; on e-ink it is simply there.
    fun enter(): EnterTransition = if (eink) EnterTransition.None else {
        coreSlideIn(initialOffsetX = { it / 3 }, animationSpec = coreTween(260)) +
            coreFadeIn(animationSpec = coreTween(180))
    }

    fun exit(): ExitTransition = if (eink) ExitTransition.None else {
        coreSlideOut(targetOffsetX = { -it / 6 }, animationSpec = coreTween(260)) +
            coreFadeOut(animationSpec = coreTween(180))
    }

    fun popEnter(): EnterTransition = if (eink) EnterTransition.None else {
        coreSlideIn(initialOffsetX = { -it / 6 }, animationSpec = coreTween(260)) +
            coreFadeIn(animationSpec = coreTween(180))
    }

    fun popExit(): ExitTransition = if (eink) ExitTransition.None else {
        coreSlideOut(targetOffsetX = { it / 3 }, animationSpec = coreTween(260)) +
            coreFadeOut(animationSpec = coreTween(180))
    }

    fun fadeIn(): EnterTransition =
        if (eink) EnterTransition.None else coreFadeIn(animationSpec = coreTween(160))

    fun fadeOut(): ExitTransition =
        if (eink) ExitTransition.None else coreFadeOut(animationSpec = coreTween(160))

    /** Rows appearing in and leaving a list. */
    fun itemPlacement(): FiniteAnimationSpec<IntOffset> = if (eink) coreSnap() else coreSpring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    companion object {
        /**
         * How long a ticked to-do stays on screen before it leaves, copied from the web client
         * and from Things itself. The pause is the point: it confirms the tap landed on the row
         * the user meant, at the moment that row is about to take the evidence away with it.
         */
        const val SETTLE_MS = 1400L
    }
}
