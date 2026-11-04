package dev.akkraizen.berill

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.mcp.McpAction
import dev.akkraizen.berill.api.mcp.McpException
import dev.akkraizen.berill.api.mcp.McpSource
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.db.BerillDatabase
import dev.akkraizen.berill.api.DeadlineSource
import dev.akkraizen.berill.api.MaintenanceSource
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.health.HealthPlugin
import dev.akkraizen.berill.plugins.health.data.Medication
import dev.akkraizen.berill.plugins.projects.ProjectsPlugin
import dev.akkraizen.berill.plugins.projects.data.Milestone
import dev.akkraizen.berill.plugins.projects.data.Project
import dev.akkraizen.berill.plugins.projects.data.ProjectTask
import dev.akkraizen.berill.plugins.projects.data.TaskStatus
import dev.akkraizen.berill.plugins.workouts.WorkoutsPlugin
import kotlinx.coroutines.flow.first
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.coroutines.runBlocking
import dev.akkraizen.berill.plugins.workouts.data.Cycle
import dev.akkraizen.berill.plugins.workouts.data.Exercise
import dev.akkraizen.berill.plugins.workouts.data.Program
import dev.akkraizen.berill.plugins.workouts.data.WorkoutDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpActionsTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val core: BerillCore

  init {
    BerillDatabase.Schema.create(driver)
    core = BerillCore(driver, BerillPlugins, scope)
  }

  @AfterTest fun close() {
    scope.cancel()
    driver.close()
  }

  private fun action(plugin: BerillPlugin, name: String): McpAction =
    plugin.provides(core.plugins.context(plugin)).filterIsInstance<McpSource>().single().actions.single { it.name == name }

  private fun run(plugin: BerillPlugin, name: String, block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
    action(plugin, name).run(buildJsonObject(block)).jsonObject

  @Test fun workoutProgramsAreAssignedToWeeksAndThePlanResolvesPercentages() {
    val programs = core.data.collection(WorkoutsPlugin.PROGRAMS, Program.serializer())
    val cycles = core.data.collection(WorkoutsPlugin.CYCLES, Cycle.serializer())
    programs.save("heavy", Program("Heavy", listOf(WorkoutDay("A", listOf(Exercise("Squat", sets = 5, reps = "5", percent = 80.0))))))
    cycles.save("block", Cycle("Block", weeks = 4, days = listOf(WorkoutDay("Base", listOf(Exercise("Run", durationSeconds = 600))))))

    run(WorkoutsPlugin, "set_one_rep_max") { put("exercise", "Squat"); put("weight", 150.0) }
    val assigned = run(WorkoutsPlugin, "assign_program") {
      put("cycle", "block"); put("program", "heavy"); putJsonArray("weeks") { add(2); add(3) }
    }
    val weeks = assigned.getValue("plan").jsonArray.map { it.jsonObject }
    assertEquals(listOf("Block", "Heavy", "Heavy", "Block"), weeks.map { it.getValue("program").jsonPrimitive.content })
    assertEquals("heavy", cycles.get("block")!!.weekPrograms.getValue(2).programId)

    val plan = run(WorkoutsPlugin, "week_plan") { put("cycle", "block"); put("week", 2) }
    val squat = plan.getValue("days").jsonArray.single().jsonObject.getValue("exercises").jsonArray.single().jsonObject
    assertEquals(120.0, squat.getValue("weightKg").jsonPrimitive.double)
    assertEquals(5, squat.getValue("sets").jsonPrimitive.int)

    run(WorkoutsPlugin, "set_override") {
      put("cycle", "block"); put("week", 2); put("day", 0); put("exercise", "squat"); put("sets", 3); put("weight", 100.0)
    }
    val overridden = run(WorkoutsPlugin, "week_plan") { put("cycle", "block"); put("week", 2); put("day", 0) }
      .getValue("days").jsonArray.single().jsonObject.getValue("exercises").jsonArray.single().jsonObject
    assertEquals(3, overridden.getValue("sets").jsonPrimitive.int)
    assertEquals(100.0, overridden.getValue("weightKg").jsonPrimitive.double)

    programs.save("heavy", Program("Heavy", listOf(WorkoutDay("A", listOf(Exercise("Squat", sets = 4, reps = "3", percent = 85.0))))))
    val linked = run(WorkoutsPlugin, "week_plan") { put("cycle", "block"); put("week", 3) }
      .getValue("days").jsonArray.single().jsonObject.getValue("exercises").jsonArray.single().jsonObject
    assertEquals("3", linked.getValue("reps").jsonPrimitive.content)

    run(WorkoutsPlugin, "reset_weeks") { put("cycle", "block"); putJsonArray("weeks") { add(2) } }
    assertNull(cycles.get("block")!!.weekPrograms[2])
    assertTrue(cycles.get("block")!!.overrides.isEmpty())

    assertFailsWith<McpException> { run(WorkoutsPlugin, "assign_program") { put("cycle", "block"); put("program", "heavy"); putJsonArray("weeks") { add(9) } } }
    assertFailsWith<McpException> { run(WorkoutsPlugin, "assign_program") { put("cycle", "block"); put("program", "missing"); put("from", 1) } }
  }

  @Test fun projectChecklistsBecomeTasksAndActionsManageThem(): Unit = runBlocking {
    val projects = core.data.collection(ProjectsPlugin.PROJECTS, Project.serializer())
    val tasks = core.data.collection(ProjectsPlugin.TASKS, ProjectTask.serializer())
    val steps = core.data.collection(ProjectsPlugin.MILESTONES, Milestone.serializer())
    projects.save("site", Project("Site", body = "## Idea\n\nLanding\n\n## Tasks\n- [x] Design\n- [ ] Build\n\n## Notes\nKeep it simple"))
    val context = core.plugins.context(ProjectsPlugin)
    ProjectsPlugin.provides(context).filterIsInstance<MaintenanceSource>().single().update()
    val migrated = tasks.all().filter { it.value.project == "site" }.sortedBy { it.value.order }
    assertEquals(listOf("Design" to TaskStatus.DONE, "Build" to TaskStatus.TODO), migrated.map { it.value.title to it.value.status })
    val body = projects.get("site")!!.body!!
    assertTrue("- [" !in body && "## Tasks" !in body && "Keep it simple" in body && "Landing" in body, body)
    ProjectsPlugin.provides(context).filterIsInstance<MaintenanceSource>().single().update()
    assertEquals(2, tasks.all().size)

    steps.save("beta", Milestone("site", "Beta"))
    val due = today().toString()
    val added = run(ProjectsPlugin, "add_task") { put("project", "Site"); put("title", "Deploy"); put("due", due); put("priority", "HIGH"); put("milestone", "Beta") }
    val id = added.getValue("id").jsonPrimitive.content
    assertEquals("beta", tasks.get(id)!!.milestone)
    val deadlines = ProjectsPlugin.provides(context).filterIsInstance<DeadlineSource>().single().deadlines().first()
    assertTrue(deadlines.any { it.title == "Site · Deploy" && it.date == today() }, deadlines.toString())

    run(ProjectsPlugin, "set_task_status") { put("task", id); put("status", "DONE") }
    assertEquals(today().toString(), tasks.get(id)!!.completed)
    val overview = run(ProjectsPlugin, "overview") { put("project", "site") }
    assertEquals(2, overview.getValue("progress").jsonObject.getValue("done").jsonPrimitive.int)
    assertEquals(3, overview.getValue("progress").jsonObject.getValue("total").jsonPrimitive.int)

    run(ProjectsPlugin, "move_project") { put("project", "site"); put("status", "IN_PROGRESS") }
    assertEquals(today().toString(), projects.get("site")!!.started)
    assertFailsWith<McpException> { run(ProjectsPlugin, "add_task") { put("project", "Missing"); put("title", "X") } }
    assertFailsWith<IllegalArgumentException> {
      ProjectsPlugin.provides(context).filterIsInstance<McpSource>().single().collections.single { it.name == ProjectsPlugin.TASKS }
        .create(buildJsonObject { put("project", "nope"); put("title", "Orphan") })
    }
  }

  @Test fun dosesAreMarkedThroughTheSameIdsAsTheChecklist() {
    val meds = core.data.collection(HealthPlugin.MEDICATIONS, Medication.serializer())
    meds.save("vit", Medication("Vitamin D", times = listOf("08:00", "20:00")))
    run(HealthPlugin, "mark_dose") { put("medication", "vitamin d"); put("status", "TAKEN") }
    val listed = run(HealthPlugin, "doses") {}.getValue("doses").jsonArray.map { it.jsonObject.getValue("status").jsonPrimitive.content }
    assertEquals(listOf("TAKEN", "NONE"), listed)
    run(HealthPlugin, "mark_dose") { put("medication", "vit"); put("status", "SKIPPED"); put("time", "20:00") }
    run(HealthPlugin, "mark_dose") { put("medication", "vit"); put("status", "NONE"); put("time", "8:00") }
    val after = run(HealthPlugin, "doses") {}.getValue("doses").jsonArray.map { it.jsonObject.getValue("status").jsonPrimitive.content }
    assertEquals(listOf("NONE", "SKIPPED"), after)
  }

  @Test fun studyDeadlinesAreAddedListedAndSubmitted() {
    val added = run(dev.akkraizen.berill.plugins.study.StudyPlugin, "add_deadline") {
      put("subject", "Сети связи"); put("title", "Лабораторная 3"); put("deadline", today().toString())
    }
    val id = added.getValue("id").jsonPrimitive.content
    assertEquals("Сети связи", added.getValue("subject").jsonPrimitive.content)
    assertEquals(0, added.getValue("daysLeft").jsonPrimitive.int)
    run(dev.akkraizen.berill.plugins.study.StudyPlugin, "add_deadline") { put("subject", "сети связи"); put("title", "Курсовая"); put("deadline", "2099-01-01") }
    assertEquals(1, core.plugins.context(dev.akkraizen.berill.plugins.study.StudyPlugin).data.raw(dev.akkraizen.berill.plugins.study.StudyPlugin.SUBJECTS).all().size)
    val upcoming = run(dev.akkraizen.berill.plugins.study.StudyPlugin, "upcoming") { put("days", 3) }.getValue("tasks").jsonArray
    assertEquals(listOf("Лабораторная 3"), upcoming.map { it.jsonObject.getValue("title").jsonPrimitive.content })
    run(dev.akkraizen.berill.plugins.study.StudyPlugin, "set_status") { put("task", id); put("status", "SUBMITTED"); put("grade", "5") }
    assertTrue(run(dev.akkraizen.berill.plugins.study.StudyPlugin, "upcoming") {}.getValue("tasks").jsonArray.isEmpty())
  }

  @Test fun habitsAreMarkedWithStreaks() {
    val habits = core.data.collection(dev.akkraizen.berill.plugins.habits.HabitsPlugin.HABITS, dev.akkraizen.berill.plugins.habits.data.HabitDef.serializer())
    habits.save("read", dev.akkraizen.berill.plugins.habits.data.HabitDef("Чтение", defaultDuration = 30))
    val yesterday = today().minus(1, kotlinx.datetime.DateTimeUnit.DAY).toString()
    run(dev.akkraizen.berill.plugins.habits.HabitsPlugin, "mark") { put("habit", "read"); put("date", yesterday) }
    val partial = run(dev.akkraizen.berill.plugins.habits.HabitsPlugin, "mark") { put("habit", "чтение"); put("value", 10) }
    assertEquals(false, partial.getValue("done").jsonPrimitive.content.toBoolean())
    assertEquals(1, partial.getValue("streak").jsonPrimitive.int)
    val done = run(dev.akkraizen.berill.plugins.habits.HabitsPlugin, "mark") { put("habit", "read"); put("value", 25); put("add", true) }
    assertEquals(35, done.getValue("value").jsonPrimitive.int)
    assertEquals(2, done.getValue("streak").jsonPrimitive.int)
    val status = run(dev.akkraizen.berill.plugins.habits.HabitsPlugin, "status") {}
    assertEquals(1, status.getValue("done").jsonPrimitive.int)
  }

  @Test fun todayFeedCollectsItemsFromSections(): Unit = runBlocking {
    val meds = core.data.collection(HealthPlugin.MEDICATIONS, Medication.serializer())
    meds.save("vit", Medication("Vitamin D", times = listOf("08:00")))
    val projects = core.data.collection(ProjectsPlugin.PROJECTS, Project.serializer())
    projects.save("p", Project("Berill"))
    core.data.collection(ProjectsPlugin.TASKS, ProjectTask.serializer()).save("t", ProjectTask("p", "Ship", due = today().minus(2, kotlinx.datetime.DateTimeUnit.DAY).toString()))
    val habits = core.data.collection(dev.akkraizen.berill.plugins.habits.HabitsPlugin.HABITS, dev.akkraizen.berill.plugins.habits.data.HabitDef.serializer())
    habits.save("read", dev.akkraizen.berill.plugins.habits.data.HabitDef("Reading", defaultDuration = 1))
    run(dev.akkraizen.berill.plugins.study.StudyPlugin, "add_deadline") { put("subject", "Math"); put("title", "Essay"); put("deadline", today().plus(1, kotlinx.datetime.DateTimeUnit.DAY).toString()) }
    val items = core.todayFeed.observe(today()).first()
    val byTitle = items.associateBy { it.title }
    assertTrue(byTitle.getValue("Ship").overdue)
    assertEquals(dev.akkraizen.berill.api.TodayKind.DOSE, byTitle.getValue("Vitamin D").kind)
    assertEquals(dev.akkraizen.berill.api.TodayKind.DEADLINE, items.single { "Essay" in it.title }.kind)
    assertEquals("Ship", items.first().title)
    byTitle.getValue("Reading").toggle!!.invoke()
    byTitle.getValue("Vitamin D").toggle!!.invoke()
    val after = core.todayFeed.observe(today()).first().associateBy { it.title }
    assertEquals(true, after.getValue("Reading").done)
    assertEquals(true, after.getValue("Vitamin D").done)
  }
}
