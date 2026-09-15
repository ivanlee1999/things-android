package us.liyifan.things.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import us.liyifan.things.model.ChecklistItem
import us.liyifan.things.model.Item
import us.liyifan.things.model.Model
import us.liyifan.things.model.Opt
import us.liyifan.things.model.Schedule
import us.liyifan.things.model.Status
import us.liyifan.things.model.TaskPatch
import us.liyifan.things.model.deadlineLabel
import us.liyifan.things.model.relativeDay
import us.liyifan.things.ui.icons.Glyph
import us.liyifan.things.ui.icons.glyph
import us.liyifan.things.ui.theme.ThingsTheme
import us.liyifan.things.ui.theme.thingsSurface

/** What the open card can do, handed in so the card itself holds no dependencies. */
data class EditorActions(
    val onSave: (TaskPatch) -> Unit,
    val onComplete: (Boolean) -> Unit,
    val onAddChecklistItem: (String) -> Unit,
    val onToggleChecklistItem: (String, Boolean) -> Unit,
    val onDeleteChecklistItem: (String) -> Unit,
    val onOpenWhen: () -> Unit,
    val onOpenDeadline: () -> Unit,
    val onOpenTags: () -> Unit,
)

/**
 * The opened to-do, drawn in place of its row.
 *
 * Edits are held locally and saved when the card closes or a field loses focus, which is what
 * the web client does and what keeps a title from being sent one keystroke at a time to a server
 * that takes a round trip to Things Cloud for each one.
 */
@Composable
fun TaskEditor(
    task: Item,
    model: Model,
    actions: EditorActions,
    modifier: Modifier = Modifier,
    autoFocusTitle: Boolean = true,
) {
    val colors = ThingsTheme.colors
    val type = ThingsTheme.type
    val checklist = model.checklistByTask[task.id].orEmpty()
    val tags = task.tagIds.mapNotNull { model.tagsById[it] }

    var title by remember(task.id) {
        mutableStateOf(TextFieldValue(task.title, TextRange(task.title.length)))
    }
    var note by remember(task.id) { mutableStateOf(TextFieldValue(task.note)) }
    var newItem by remember(task.id) { mutableStateOf<String?>(null) }
    val titleFocus = remember { FocusRequester() }

    fun save() {
        val patch = TaskPatch(
            title = if (title.text.trim() != task.title && title.text.isNotBlank()) {
                Opt.of(title.text.trim())
            } else Opt.Absent,
            note = if (note.text != task.note) Opt.of(note.text) else Opt.Absent,
        )
        if (!patch.isEmpty) actions.onSave(patch)
    }

    LaunchedEffect(task.id) {
        if (autoFocusTitle) runCatching { titleFocus.requestFocus() }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .thingsSurface(RoundedCornerShape(ThingsTheme.dims.cardRadius))
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
            ThingsCheckbox(
                checked = task.status == Status.COMPLETED,
                round = task.isProject,
                onChange = { actions.onComplete(it) },
                modifier = Modifier.padding(top = 3.dp),
            )
            EditorField(
                value = title,
                onValueChange = { title = it },
                placeholder = "New To-Do",
                style = type.row.copy(color = colors.text, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                modifier = Modifier.weight(1f).focusRequester(titleFocus),
                onDone = { save() },
            )
        }

        EditorField(
            value = note,
            onValueChange = { note = it },
            placeholder = "Notes",
            style = type.note.copy(color = colors.text),
            modifier = Modifier.fillMaxWidth().padding(start = 31.dp),
            onDone = { save() },
        )

        if (checklist.isNotEmpty() || newItem != null) {
            ChecklistEditor(
                items = checklist,
                draft = newItem,
                onDraftChange = { newItem = it },
                onCommitDraft = { text ->
                    if (text.isNotBlank()) {
                        actions.onAddChecklistItem(text.trim())
                        newItem = ""
                    } else {
                        newItem = null
                    }
                },
                onToggle = actions.onToggleChecklistItem,
                onDelete = actions.onDeleteChecklistItem,
                modifier = Modifier.padding(start = 31.dp, top = 6.dp),
            )
        }

        if (tags.isNotEmpty()) {
            FlowRow(
                Modifier.padding(start = 31.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { Chip(label = it.title, active = false) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val whenLabel = when {
                task.scheduledDate != null -> relativeDay(task.scheduledDate, model.today)
                task.schedule == Schedule.SOMEDAY -> "Someday"
                else -> null
            }
            FooterButton(
                icon = if (whenLabel == "Today") Glyph.Star else Glyph.Calendar,
                tint = if (whenLabel == "Today") colors.today else colors.text2,
                label = whenLabel,
                onClick = { save(); actions.onOpenWhen() },
            )
            Spacer(Modifier.weight(1f))
            FooterButton(icon = Glyph.Tag, onClick = { save(); actions.onOpenTags() })
            FooterButton(icon = Glyph.Checklist, onClick = { newItem = "" })
            val deadline = task.deadline?.let { deadlineLabel(it, model.today) }
            FooterButton(
                icon = Glyph.Flag,
                tint = if (deadline != null) colors.deadline else colors.text2,
                label = deadline?.text,
                labelColor = colors.deadline,
                onClick = { save(); actions.onOpenDeadline() },
            )
        }
    }
}

@Composable
private fun EditorField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val colors = ThingsTheme.colors
    Box(modifier) {
        if (value.text.isEmpty()) {
            Text(placeholder, style = style, color = colors.text3)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = SolidColor(colors.blue),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FooterButton(
    icon: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ThingsTheme.colors.text2,
    label: String? = null,
    labelColor: Color = ThingsTheme.colors.text,
) {
    Row(
        modifier
            .padding(horizontal = 2.dp)
            .size(width = if (label == null) ThingsTheme.dims.iconButton else androidx.compose.ui.unit.Dp.Unspecified, height = ThingsTheme.dims.iconButton)
            .then(Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            icon = glyph(icon, 16f),
            contentDescription = null,
            tint = tint,
            onClick = onClick,
            iconSize = 16.dp,
        )
        if (label != null) {
            Text(label, style = ThingsTheme.type.sub, color = labelColor)
        }
    }
}

/** The checklist inside the card: read-only titles, a tick, a cross, and a field for the next. */
@Composable
fun ChecklistEditor(
    items: List<ChecklistItem>,
    draft: String?,
    onDraftChange: (String?) -> Unit,
    onCommitDraft: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThingsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThingsCheckbox(
                    checked = item.status == Status.COMPLETED,
                    onChange = { onToggle(item.id, it) },
                    size = 15.dp,
                )
                Text(
                    text = item.title,
                    style = if (item.status == Status.COMPLETED) ThingsTheme.type.rowDone else ThingsTheme.type.sub,
                    color = if (item.status == Status.COMPLETED) colors.text3 else colors.text,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    icon = glyph(Glyph.X, 10f),
                    contentDescription = "Remove item",
                    onClick = { onDelete(item.id) },
                    iconSize = 10.dp,
                )
            }
        }
        if (draft != null) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThingsCheckbox(checked = false, onChange = null, size = 15.dp)
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text("New item", style = ThingsTheme.type.sub, color = colors.text3)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        textStyle = ThingsTheme.type.sub.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.blue),
                        singleLine = true,
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            // Enter adds the item and leaves a fresh field, so a list can be
                            // typed straight through without reaching for the button each time.
                            onDone = { onCommitDraft(draft) },
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
        }
    }
}
