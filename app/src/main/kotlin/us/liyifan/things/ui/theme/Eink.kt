package us.liyifan.things.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * The pressed state for e-ink, in place of a ripple.
 *
 * A ripple is an animation made of alpha, which is the pair of things an electrophoretic panel
 * cannot do: it arrives as a run of partial refreshes that smear and leave a grey ghost where
 * the finger was. What reads instantly on e-paper is a flat block that appears and disappears in
 * one update, so that is what a press draws here.
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
) : Modifier.Node(), DrawModifierNode {

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
        if (pressed) drawRect(color = ink.copy(alpha = 0.18f))
    }
}
