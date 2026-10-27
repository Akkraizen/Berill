package dev.akkraizen.berill.plugins.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.DeleteButton
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.fields.BodyEditor
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.fields.normalizeCommaList
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.FieldWithActions
import dev.akkraizen.berill.api.ui.layout.FieldsRow
import dev.akkraizen.berill.api.ui.layout.KanbanBoard
import dev.akkraizen.berill.api.ui.layout.KanbanColumn
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.navigation.rememberOpenLink
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.selection.pastelCheckboxColors
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.api.ui.today

@Composable
internal fun ProjectsBoard(ctx: PluginContext) {
  val collection = remember(ctx) { projects(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  var idea by rememberDraft("projects/board/idea")
  var error by remember { mutableStateOf<String?>(null) }
  fun addIdea() {
    val name = idea.trim()
    when {
      name.isEmpty() -> error = ProjectsText.enterName()
      all.any { it.value.title.equals(name, ignoreCase = true) } ->
        error = ProjectsText.alreadyExists()

      else -> {
        val id = ctx.data.newId()
        collection.save(id, Project(title = name, created = today().toString(), body = TEMPLATE))
        idea = ""
        ctx.nav.open(ProjectsPlugin.PROJECT_ROUTE + id)
      }
    }
  }

  PluginList {
    item {
      FieldWithActions(
        field = {
          BerillTextField(
            idea,
            {
              idea = it
              error = null
            },
            placeholder = { Text(ProjectsText.newIdea(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Ic(Icons.Outlined.Lightbulb) },
            singleLine = true,
            isError = error != null,
            supportingText = if (error != null) {
              { Text(error ?: "") }
            } else
              null,
            modifier = Modifier.fillMaxWidth(),
            onSubmit = { addIdea() }
          )
        },
        actions = {
          Button(onClick = { addIdea() }) { Text(ProjectsText.add()) }
        }
      )
    }
    item {
      val byStatus = all.groupBy { it.value.normalizedStatus }
      KanbanBoard(
        columns = STATUSES.map { s ->
          KanbanColumn(
            s,
            STATUS_LABELS.getValue(s),
            byStatus[s].orEmpty().sortedBy { it.value.title.lowercase() },
            ProjectsText.empty(),
            STATUS_ICONS[s]
          )
        },
        itemKey = { it.id },
        onMove = { item, status ->
          collection
            .get(item.id)
            ?.takeIf { it.normalizedStatus != status }
            ?.let { collection.save(item.id, it.moveTo(status)) }
        },
        card = { item ->
          ProjectCard(item) { ctx.nav.open(ProjectsPlugin.PROJECT_ROUTE + item.id) }
        }
      )
    }
    item { ProjectsStatistics(all.map { it.value }) }
  }
}

@Composable
private fun ProjectCard(item: Item<Project>, onOpen: () -> Unit) {
  val p = item.value
  val openLink = rememberOpenLink()
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      p.title,
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.interactive(bleed = 6.dp, onClick = onOpen)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      p.site?.let { url ->
        LinkLabel(Icons.Outlined.Language, ProjectsText.site()) { openLink(url) }
      }
      p.repo?.let { url ->
        LinkLabel(Icons.Outlined.Code, ProjectsText.repository()) { openLink(url) }
      }
    }
    p.description?.let {
      Text(
        it,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
      )
    }
    if (p.stack.isNotEmpty())
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        p.stack.forEach {
          BerillChip(
            onClick = null,
            label = { Text(it, style = MaterialTheme.typography.labelSmall) })
        }
      }
    val checks = checklist(p.body)
    if (checks.isNotEmpty()) {
      val done = checks.count { it.done }
      ProgressLine(done / checks.size.toFloat())
      Text("$done/${checks.size}", style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
internal fun ProjectScreen(ctx: PluginContext, id: String) {
  val collection = remember(ctx) { projects(ctx) }
  val project by
  remember(collection, id) { collection.observe(id) }.collectAsState(collection.get(id))
  val p = project
    ?: run {
      EmptyHint(ProjectsText.deleted())
      return
    }

  fun update(transform: (Project) -> Project) {
    val current = collection.get(id) ?: return
    collection.save(id, transform(current))
  }

  var newTask by rememberDraft("projects/task/$id")
  fun addTask() {
    val text = newTask.trim()
    update { it.copy(body = addCheck(it.body, text)) }
    newTask = ""
  }

  val checks = checklist(p.body)

  PluginList {
    item {
      DetailHeader(p.title.ifBlank { ProjectsText.untitled() }, STATUS_ICONS[p.normalizedStatus]) {
        DeleteButton(ProjectsText.projectNoun()) {
          collection.delete(id)
          ctx.nav.back()
        }
      }
    }
    item {
      ChoiceChips(
        STATUSES,
        p.normalizedStatus,
        { STATUS_LABELS.getValue(it) },
        { s -> update { it.moveTo(s) } },
        icon = { STATUS_ICONS[it] }
      )
    }
    item {
      TwoPane(
        left = {
          SectionCard(ProjectsText.tasks(), Icons.Outlined.TaskAlt) {
            if (checks.isEmpty())
              EmptyHint(ProjectsText.noTasks())
            else
              ProgressLine(checks.count { it.done } / checks.size.toFloat())
            checks.forEach { c ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(colors = pastelCheckboxColors(), 
                  checked = c.done,
                  onCheckedChange = {
                    update { it.copy(body = toggleCheck(it.body.orEmpty(), c.line, c.text)) }
                  }
                )
                Text(
                  c.text,
                  textDecoration = if (c.done) TextDecoration.LineThrough else null,
                  color = if (c.done)
                    MaterialTheme.colorScheme.onSurfaceVariant
                  else
                    MaterialTheme.colorScheme.onSurface
                )
              }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              BerillTextField(
                newTask,
                { newTask = it },
                placeholder = { Text(ProjectsText.newTask(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                onSubmit = { if (newTask.isNotBlank()) addTask() }
              )
              TextButton(enabled = newTask.isNotBlank(), onClick = { addTask() }) {
                Icon(BerillIcons.Add, contentDescription = ProjectsText.addTask())
              }
            }
          }
          SectionCard(ProjectsText.descriptionAndNotes(), Icons.Outlined.Description) {
            BodyEditor(
              p.body ?: "",
              { v -> update { it.copy(body = v.ifBlank { null }) } },
              TEMPLATE
            )
          }
        },
        right = {
          SectionCard(ProjectsText.properties()) {
            CommitTextField(
              p.title,
              { v -> update { it.copy(title = v.trim()) } },
              ProjectsText.name()
            )
            CommitTextField(
              p.description ?: "",
              { v -> update { it.copy(description = v.trim().ifEmpty { null }) } },
              ProjectsText.summary(),
              singleLine = false
            )
            CommitTextField(
              p.stack.joinToString(", "),
              { v ->
                update {
                  it.copy(stack = v.split(',').map(String::trim).filter(String::isNotEmpty))
                }
              },
              ProjectsText.stack(),
              normalize = ::normalizeCommaList
            )
            CommitTextField(
              p.repo ?: "",
              { v -> update { it.copy(repo = v.trim().ifEmpty { null }) } },
              ProjectsText.repositoryLabel()
            )
            CommitTextField(
              p.site ?: "",
              { v -> update { it.copy(site = v.trim().ifEmpty { null }) } },
              ProjectsText.siteLabel()
            )
            FieldsRow {
              DateField(
                p.started,
                { v -> update { it.copy(started = v) } },
                ProjectsText.started()
              )
              DateField(
                p.finished,
                { v -> update { it.copy(finished = v) } },
                ProjectsText.finished()
              )
            }
          }
        }
      )
    }
  }
}

@Composable
private fun LinkLabel(icon: ImageVector, text: String, onClick: () -> Unit) {
  Row(
    Modifier.interactive(bleed = 6.dp, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Ic(icon, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
    Text(
      text,
      color = MaterialTheme.colorScheme.primary,
      style = MaterialTheme.typography.bodySmall
    )
  }
}
