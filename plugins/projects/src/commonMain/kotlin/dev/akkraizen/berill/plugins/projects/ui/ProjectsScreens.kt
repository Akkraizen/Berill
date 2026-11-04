package dev.akkraizen.berill.plugins.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import dev.akkraizen.berill.api.ui.layout.CardGrid
import dev.akkraizen.berill.api.ui.layout.ListLayout
import dev.akkraizen.berill.api.ui.layout.ListLayoutSwitcher
import dev.akkraizen.berill.api.ui.layout.rememberListLayout
import dev.akkraizen.berill.plugins.projects.data.BOARD_LAYOUT
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.akkraizen.berill.api.ui.fields.SearchField
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
import dev.akkraizen.berill.api.ui.layout.TAB_STATISTICS
import dev.akkraizen.berill.api.ui.layout.TabbedPluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.navigation.rememberOpenLink
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.selection.DropdownField
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.projects.ProjectsPlugin
import dev.akkraizen.berill.plugins.projects.data.MILESTONE_ORDER
import dev.akkraizen.berill.plugins.projects.data.Milestone
import dev.akkraizen.berill.plugins.projects.data.Progress
import dev.akkraizen.berill.plugins.projects.data.Project
import dev.akkraizen.berill.plugins.projects.data.ProjectStatus
import dev.akkraizen.berill.plugins.projects.data.ProjectTask
import dev.akkraizen.berill.plugins.projects.data.STATUSES
import dev.akkraizen.berill.plugins.projects.data.TEMPLATE
import dev.akkraizen.berill.plugins.projects.data.deleteProject
import dev.akkraizen.berill.plugins.projects.data.migrateChecklist
import dev.akkraizen.berill.plugins.projects.data.milestones
import dev.akkraizen.berill.plugins.projects.data.normalizeTags
import dev.akkraizen.berill.plugins.projects.data.progress
import dev.akkraizen.berill.plugins.projects.data.projectTasks
import dev.akkraizen.berill.plugins.projects.data.projects
import dev.akkraizen.berill.plugins.projects.i18n.ProjectsText

private enum class ProjectSort { NAME, DEADLINE, PROGRESS, RECENT, MANUAL }

private val PROJECTS_ORDER = dev.akkraizen.berill.api.ui.layout.ManualOrder("listOrder")

private fun sortLabel(sort: ProjectSort): String = when (sort) {
  ProjectSort.NAME -> ProjectsText.sortName()
  ProjectSort.DEADLINE -> ProjectsText.sortDeadline()
  ProjectSort.PROGRESS -> ProjectsText.sortProgress()
  ProjectSort.RECENT -> ProjectsText.sortRecent()
  ProjectSort.MANUAL -> ProjectsText.sortManual()
}

private class ProjectInfo(val item: Item<Project>, val progress: Progress, val next: Milestone?)

@Composable
internal fun ProjectsBoard(ctx: PluginContext) {
  val collection = remember(ctx) { projects(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  val tasks by remember(ctx) { projectTasks(ctx).observe() }.collectAsState(emptyList())
  val steps by remember(ctx) { milestones(ctx).observe() }.collectAsState(emptyList())
  var idea by rememberDraft("projects/board/idea")
  var error by remember { mutableStateOf<String?>(null) }
  var query by rememberSaveable { mutableStateOf("") }
  var tag by rememberSaveable { mutableStateOf<String?>(null) }
  var sort by rememberSaveable { mutableIntStateOf(ProjectSort.NAME.ordinal) }
  var archive by rememberSaveable { mutableStateOf(false) }
  val layout = rememberListLayout(ctx, BOARD_LAYOUT)
  val modes = dev.akkraizen.berill.api.ui.layout.rememberCollectionModes()
  val manual = dev.akkraizen.berill.api.ui.layout.rememberManualOrder(ctx, PROJECTS_ORDER)

  fun addIdea() {
    val name = idea.trim()
    when {
      name.isEmpty() -> error = ProjectsText.enterName()
      all.any { it.value.title.equals(name, ignoreCase = true) } -> error = ProjectsText.alreadyExists()
      else -> {
        val id = ctx.data.newId()
        collection.save(id, Project(title = name, created = today().toString(), body = TEMPLATE, tasksMigrated = true))
        idea = ""
        ctx.nav.open(ProjectsPlugin.PROJECT_ROUTE + id)
      }
    }
  }

  val tasksByProject = tasks.groupBy { it.value.project }
  val stepsByProject = steps.groupBy { it.value.project }
  val tags = all.filterNot { it.value.archived }.flatMap { it.value.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
  val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
  val visible = all
    .filter { it.value.archived == archive }
    .filter { item -> tag == null || item.value.tags.any { it.equals(tag, ignoreCase = true) } }
    .filter { item ->
      val p = item.value
      val text = (listOf(p.title, p.description.orEmpty()) + p.stack + p.tags + tasksByProject[item.id].orEmpty().map { it.value.title }).joinToString(" ").lowercase()
      terms.all { it in text }
    }
    .map { item ->
      ProjectInfo(
        item,
        tasksByProject[item.id].orEmpty().map { it.value }.progress(),
        stepsByProject[item.id].orEmpty().filterNot { it.value.done }.sortedWith(MILESTONE_ORDER).firstOrNull()?.value
      )
    }
    .sortedWith(
      when (ProjectSort.entries[sort]) {
        ProjectSort.NAME -> compareBy { it.item.value.title.lowercase() }
        ProjectSort.DEADLINE -> compareBy<ProjectInfo> { it.item.value.deadline ?: "9999" }.thenBy { it.item.value.title.lowercase() }
        ProjectSort.PROGRESS -> compareByDescending<ProjectInfo> { it.progress.fraction }.thenBy { it.item.value.title.lowercase() }
        ProjectSort.RECENT -> compareByDescending<ProjectInfo> { it.item.value.started ?: it.item.value.created ?: "" }.thenBy { it.item.value.title.lowercase() }
        ProjectSort.MANUAL -> compareBy { it.item.value.title.lowercase() }
      }
    )
    .let { list -> if (ProjectSort.entries[sort] == ProjectSort.MANUAL) dev.akkraizen.berill.api.ui.layout.applyManualOrder(list, manual) { it.item.id } else list }

  TabbedPluginList { tab ->
    if (tab == TAB_STATISTICS) {
      item { ProjectsStatistics(all.map { it.value }, tasks.map { it.value }) }
      return@TabbedPluginList
    }
    if (!archive) item {
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
            supportingText = error?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth(),
            onSubmit = { addIdea() }
          )
        },
        actions = { Button(onClick = { addIdea() }) { Text(ProjectsText.add()) } }
      )
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldWithActions(
          field = { SearchField(query, { query = it }, ProjectsText.searchProjects()) },
          actions = {
            DropdownField(ProjectsText.sort(), ProjectSort.entries, ProjectSort.entries[sort], ::sortLabel, { sort = it.ordinal }, Modifier.width(290.dp))
            ListLayoutSwitcher(ctx, BOARD_LAYOUT, modes.takeIf { layout != ListLayout.BOARD || archive }, canReorder = ProjectSort.entries[sort] == ProjectSort.MANUAL)
          }
        )
        if (!modes.idle) dev.akkraizen.berill.api.ui.layout.CollectionModeBar(
          modes,
          listOf(
            dev.akkraizen.berill.api.ui.layout.BulkAction(
              dev.akkraizen.berill.api.ui.UiText.bulkStatus(), Icons.Outlined.SwapHoriz,
              choices = STATUSES.map { status -> STATUS_LABELS.getValue(status) to { ids: List<String> -> ids.forEach { id -> collection.get(id)?.let { collection.save(id, it.moveTo(status)) } } } }
            ),
            dev.akkraizen.berill.api.ui.layout.BulkAction(if (archive) ProjectsText.unarchive() else ProjectsText.toArchive(), if (archive) Icons.Outlined.Unarchive else Icons.Outlined.Archive) { ids ->
              ids.forEach { id -> collection.get(id)?.let { collection.save(id, it.copy(archived = !archive)) } }
            },
            dev.akkraizen.berill.api.ui.layout.BulkAction(dev.akkraizen.berill.api.ui.UiText.bulkDelete(), BerillIcons.Delete, destructive = true) { ids -> ids.forEach(collection::delete) }
          ),
          visible.map { it.item.id }
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          BerillChip(selected = tag == null && !archive, onClick = { tag = null; archive = false }, label = { Text(ProjectsText.allProjects()) })
          tags.forEach { value ->
            BerillChip(selected = tag == value, onClick = { tag = if (tag == value) null else value }, label = { Text("#$value", maxLines = 1, overflow = TextOverflow.Ellipsis) })
          }
          BerillChip(
            selected = archive,
            onClick = { archive = !archive },
            leadingIcon = { Ic(Icons.Outlined.Inventory2, size = 18.dp) },
            label = { Text(ProjectsText.archive() + " · " + all.count { it.value.archived }) }
          )
        }
      }
    }
    val open: (ProjectInfo) -> Unit = { info -> ctx.nav.open(ProjectsPlugin.PROJECT_ROUTE + info.item.id) }
    if (archive) {
      if (visible.isEmpty()) item { EmptyHint(ProjectsText.archiveEmpty()) }
      else item { ProjectGroup(visible, if (layout == ListLayout.ROWS) ListLayout.ROWS else ListLayout.CARDS, open, modes) { ids -> PROJECTS_ORDER.save(ctx, ids) } }
      return@TabbedPluginList
    }
    if (visible.isEmpty() && (terms.isNotEmpty() || tag != null)) item { EmptyHint(ProjectsText.nothingHere()) }
    if (layout != ListLayout.BOARD) {
      val byStatus = visible.groupBy { it.item.value.normalizedStatus }
      STATUSES.forEach { status ->
        val group = byStatus[status].orEmpty()
        if (group.isEmpty()) return@forEach
        item(key = "status-${status.name}") {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusHeader(status, group.size)
            ProjectGroup(group, layout, open, modes) { ids -> PROJECTS_ORDER.save(ctx, ids) }
          }
        }
      }
      return@TabbedPluginList
    }
    item {
      val byStatus = visible.groupBy { it.item.value.normalizedStatus }
      KanbanBoard(
        columns = STATUSES.map { s ->
          KanbanColumn(s.name, STATUS_LABELS.getValue(s), byStatus[s].orEmpty(), ProjectsText.empty(), STATUS_ICONS[s])
        },
        itemKey = { it.item.id },
        onMove = { info, statusKey ->
          val status = ProjectStatus.valueOf(statusKey)
          collection.get(info.item.id)?.takeIf { it.normalizedStatus != status }?.let { collection.save(info.item.id, it.moveTo(status)) }
        },
        card = { info -> ProjectCard(info) { ctx.nav.open(ProjectsPlugin.PROJECT_ROUTE + info.item.id) } }
      )
    }
  }
}

@Composable
private fun StatusHeader(status: ProjectStatus, count: Int) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    STATUS_ICONS[status]?.let { Ic(it, size = 20.dp, tint = MaterialTheme.colorScheme.primary) }
    Text(STATUS_LABELS.getValue(status), style = MaterialTheme.typography.titleMedium)
    Text("$count", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun ProjectGroup(items: List<ProjectInfo>, layout: ListLayout, onOpen: (ProjectInfo) -> Unit, modes: dev.akkraizen.berill.api.ui.layout.CollectionModes, onReorder: (List<String>) -> Unit) {
  val ids = androidx.compose.runtime.rememberUpdatedState(items.map { it.item.id })
  if (layout == ListLayout.ROWS)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
      Column {
        items.forEachIndexed { index, info ->
          if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          dev.akkraizen.berill.api.ui.layout.SelectableItem(modes, info.item.id, { ids.value }, onReorder) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) { ProjectRow(info) { onOpen(info) } }
          }
        }
      }
    }
  else
    CardGrid(items, minCardWidth = 260.dp, maxColumns = 4) { info, modifier ->
      Box(modifier) {
        dev.akkraizen.berill.api.ui.layout.SelectableItem(modes, info.item.id, { ids.value }, onReorder) {
          Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.padding(14.dp)) { ProjectCard(info) { onOpen(info) } }
          }
        }
      }
    }
}

@Composable
private fun ProjectRow(info: ProjectInfo, onOpen: () -> Unit) {
  val p = info.item.value
  Row(
    Modifier.fillMaxWidth().interactive(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    STATUS_ICONS[p.normalizedStatus]?.let { Ic(it, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      val line = listOfNotNull(p.description, p.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }).joinToString(" · ")
      if (line.isNotEmpty())
        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    DueLabel(p.deadline, p.normalizedStatus == ProjectStatus.READY)
    if (info.progress.total > 0)
      Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ProgressLine(info.progress.fraction)
        Text(ProjectsText.progressOf(info.progress.done, info.progress.total)(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
  }
}

@Composable
private fun ProjectCard(info: ProjectInfo, onOpen: () -> Unit) {
  val p = info.item.value
  val openLink = rememberOpenLink()
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(p.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.interactive(bleed = 6.dp, onClick = onOpen))
    if (p.site != null || p.repo != null || p.deadline != null)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DueLabel(p.deadline, p.normalizedStatus == ProjectStatus.READY)
        p.site?.let { url -> LinkLabel(Icons.Outlined.Language, ProjectsText.site()) { openLink(url) } }
        p.repo?.let { url -> LinkLabel(Icons.Outlined.Code, ProjectsText.repository()) { openLink(url) } }
      }
    p.description?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    TagChips(p.tags)
    if (p.stack.isNotEmpty())
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        p.stack.forEach {
          BerillChip(onClick = null, label = { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
      }
    if (info.progress.total > 0) {
      ProgressLine(info.progress.fraction)
      Text(ProjectsText.progressOf(info.progress.done, info.progress.total)(), style = MaterialTheme.typography.labelSmall)
    }
    info.next?.let { step ->
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(ProjectsText.nextMilestone(step.title)(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        DueLabel(step.due)
      }
    }
  }
}

@Composable
internal fun ProjectScreen(ctx: PluginContext, id: String) {
  val collection = remember(ctx) { projects(ctx) }
  val project by remember(collection, id) { collection.observe(id) }.collectAsState(collection.get(id))
  LaunchedEffect(id, project?.tasksMigrated) { if (project?.tasksMigrated == false) migrateChecklist(ctx, id) }
  val p = project ?: run {
    EmptyHint(ProjectsText.deleted())
    return
  }

  fun update(transform: (Project) -> Project) {
    val current = collection.get(id) ?: return
    collection.save(id, transform(current))
  }

  PluginList {
    item {
      DetailHeader(p.title.ifBlank { ProjectsText.untitled() }, STATUS_ICONS[p.normalizedStatus]) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          TextButton(onClick = { update { it.copy(archived = !it.archived) } }) {
            Ic(if (p.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, size = 18.dp)
            Text(" " + if (p.archived) ProjectsText.unarchive() else ProjectsText.toArchive())
          }
          DeleteButton(ProjectsText.projectNoun()) {
            deleteProject(ctx, id)
            ctx.nav.back()
          }
        }
      }
    }
    item {
      ChoiceChips(STATUSES, p.normalizedStatus, { STATUS_LABELS.getValue(it) }, { s -> update { it.moveTo(s) } }, icon = { STATUS_ICONS[it] })
    }
    item {
      TwoPane(
        left = {
          TasksCard(ctx, id, p)
          MilestonesCard(ctx, id)
          SectionCard(ProjectsText.descriptionAndNotes(), Icons.Outlined.Description, collapseKey = "projects/description") {
            BodyEditor(p.body ?: "", { v -> update { it.copy(body = v.ifBlank { null }) } }, TEMPLATE)
          }
        },
        right = {
          SectionCard(ProjectsText.properties()) {
            CommitTextField(p.title, { v -> update { it.copy(title = v.trim()) } }, ProjectsText.name())
            CommitTextField(p.description ?: "", { v -> update { it.copy(description = v.trim().ifEmpty { null }) } }, ProjectsText.summary(), singleLine = false)
            CommitTextField(
              p.tags.joinToString(", "),
              { v -> update { it.copy(tags = normalizeTags(v.split(','))) } },
              ProjectsText.tags(),
              normalize = ::normalizeCommaList
            )
            CommitTextField(
              p.stack.joinToString(", "),
              { v -> update { it.copy(stack = v.split(',').map(String::trim).filter(String::isNotEmpty)) } },
              ProjectsText.stack(),
              normalize = ::normalizeCommaList
            )
            DateField(p.deadline, { v -> update { it.copy(deadline = v) } }, ProjectsText.deadline())
            CommitTextField(p.repo ?: "", { v -> update { it.copy(repo = v.trim().ifEmpty { null }) } }, ProjectsText.repositoryLabel())
            CommitTextField(p.site ?: "", { v -> update { it.copy(site = v.trim().ifEmpty { null }) } }, ProjectsText.siteLabel())
            FieldsRow(minItemWidth = 160.dp) {
              DateField(p.started, { v -> update { it.copy(started = v) } }, ProjectsText.started())
              DateField(p.finished, { v -> update { it.copy(finished = v) } }, ProjectsText.finished())
            }
          }
          LinkedNotesCard(ctx, p) { change -> update(change) }
        }
      )
    }
  }
}

@Composable
private fun LinkLabel(icon: ImageVector, text: String, onClick: () -> Unit) {
  Row(Modifier.interactive(bleed = 6.dp, onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Ic(icon, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
    Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
  }
}
