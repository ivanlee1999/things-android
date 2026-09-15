package us.liyifan.things.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.Modifier.Node
import kotlinx.coroutines.launch

/**
 * The pressed state for e-ink, in place of a ripple.
 *
 * A ripple is an animation made of alpha, which is the two things an electrophoretic panel
 * cannot do: it arrives as a sequence of partial refreshes that smear and leave a grey ghost
 * where the finger was. What reads instantly on e-paper is inversion — the row goes black, the
 * text goes white, one refresh, no animation — so that is what a press does here.
 */
class EinkPressIndication(private val ink: Color) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        EinkPressNode(interactionSource, ink)

    override fun equals(other: Any?): Boolean = other is EinkPressIndication && other.ink == ink

    override fun hashCode(): Int = ink.hashCode()
}

private class EinkPressNode(
    private val interactionSource: InteractionSource,
    private val ink: Color,
) : Node(), DrawModifierNode {

    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            var count = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> count++
                    is PressInteraction.Release, is PressInteraction.Cancel -> count--
                }
                val nowPressed = count > 0
                if (nowPressed != pressed) {
                    pressed = nowPressed
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        // A flat overlay at full strength: one refresh to show, one to clear.
        if (pressed) drawRect(color = ink.copy(alpha = 0.18f))
    }
}

/** No indication at all, for surfaces where even inversion would be noise. */
object NoIndication : Indication {
    @Suppress("DEPRECATION")
    @androidx.compose.runtime.Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource) =
        androidx.compose.foundation.IndicationInstance { drawContent() }
}

/** A convenience for `if (eink) a else b`, so the branch reads as the rule it is. */
fun <T> einkOr(eink: Boolean, onEink: T, otherwise: T): T = if (eink) onEink else otherwise

/** Nothing outside this file should reach for Modifier directly to express an e-ink rule. */
internal val NoModifier: Modifier = Modifier
