package dev.akkraizen.berill.plugins.workouts

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.AutoScrollLazyColumn as LazyColumn
import dev.akkraizen.berill.api.ui.media.Cover
import kotlinx.coroutines.delay

internal class ExerciseImages(val state: ExerciseDbState, private val overrides: Map<String, String>) {
  private val cache = HashMap<String, DbExercise?>()

  fun of(ex: CatalogExercise): DbExercise? {
    val ready = state as? ExerciseDbState.Ready ?: return null
    return cache.getOrPut(ex.id) { ready.imageFor(ex, overrides) }
  }

  fun isChosenByUser(ex: CatalogExercise) = ex.id in overrides
}

@Composable
internal fun rememberExerciseImages(ctx: PluginContext): ExerciseImages {
  LaunchedEffect(ctx) { ExerciseDb.ensureLoaded(ctx) }
  val state by ExerciseDb.state.collectAsState()
  val raw by
    remember(ctx) { ctx.settings.observe(EXERCISE_IMAGES_KEY) }
      .collectAsState(ctx.settings.get(EXERCISE_IMAGES_KEY))
  return remember(state, raw) { ExerciseImages(state, readImageOverrides(raw)) }
}

@Composable
internal fun ExerciseThumb(image: DbExercise?, size: Dp = 40.dp) {
  Cover(
    image?.urls?.firstOrNull(),
    Icons.Outlined.FitnessCenter,
    Modifier.size(size),
    shape = MaterialTheme.shapes.small
  )
}

@Composable
internal fun ExerciseAnimation(image: DbExercise, modifier: Modifier = Modifier, playing: Boolean = true) {
  val urls = image.urls
  var frame by remember(image.id) { mutableIntStateOf(0) }
  LaunchedEffect(image.id, playing) {
    while (playing && urls.size > 1) {
      delay(1100)
      frame = (frame + 1) % urls.size
    }
  }
  Crossfade(targetState = frame, animationSpec = tween(350), modifier = modifier, label = "exercise-frame") { i ->
    Cover(
      urls[i.coerceIn(0, urls.lastIndex)],
      Icons.Outlined.FitnessCenter,
      Modifier.fillMaxWidth().height(240.dp),
      contentScale = ContentScale.Fit
    )
  }
}

@Composable
internal fun ExerciseImageCard(ctx: PluginContext, ex: CatalogExercise, images: ExerciseImages) {
  var picking by remember { mutableStateOf(false) }
  var playing by remember { mutableStateOf(true) }
  var showSteps by remember { mutableStateOf(false) }
  val image = images.of(ex)
  SectionCard(
    CatalogText.howItLooks(),
    Icons.Outlined.Image,
    trailing = {
      if (image != null && image.urls.size > 1)
        IconButton(onClick = { playing = !playing }) {
          Ic(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow)
        }
    }
  ) {
    when (val s = images.state) {
      ExerciseDbState.Idle,
      ExerciseDbState.Loading ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
          Text(CatalogText.imagesLoading(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

      is ExerciseDbState.Failed -> {
        Text(CatalogText.imagesFailed(s.message)(), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { ExerciseDb.ensureLoaded(ctx, retry = true) }) {
          Text(CatalogText.retry())
        }
      }

      is ExerciseDbState.Ready -> {
        if (image != null) {
          ExerciseAnimation(image, Modifier.fillMaxWidth(), playing)
          Text(
            image.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          EmptyHint(CatalogText.noImage())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(onClick = { picking = true }) {
            Text(if (image == null) CatalogText.pickImage() else CatalogText.changeImage())
          }
          if (images.isChosenByUser(ex))
            TextButton(onClick = { setExerciseImage(ctx, ex.id, null) }) {
              Text(CatalogText.autoImage())
            }
          else if (image != null)
            TextButton(onClick = { setExerciseImage(ctx, ex.id, "") }) {
              Text(CatalogText.removeImage())
            }
        }
        if (image != null && image.instructions.isNotEmpty()) {
          TextButton(onClick = { showSteps = !showSteps }) {
            Text(if (showSteps) CatalogText.hideSteps() else CatalogText.showSteps())
          }
          if (showSteps)
            image.instructions.forEachIndexed { i, step ->
              Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
          CatalogText.imagesCredit(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (picking) {
          ImagePickerDialog(
            db = s,
            initialQuery = ex.englishName.takeIf { ex.builtinKey != null }.orEmpty(),
            onDismiss = { picking = false },
            onPick = { picked ->
              setExerciseImage(ctx, ex.id, picked.id)
              picking = false
            }
          )
        }
      }
    }
  }
}

@Composable
private fun ImagePickerDialog(
  db: ExerciseDbState.Ready,
  initialQuery: String,
  onDismiss: () -> Unit,
  onPick: (DbExercise) -> Unit
) {
  var query by remember { mutableStateOf(initialQuery) }
  val found = remember(query) { db.search(query) }
  DialogKeys(onConfirm = found.singleOrNull()?.let { only -> { onPick(only) } }, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(CatalogText.pickImageTitle()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BerillTextField(
          query,
          { query = it },
          placeholder = { Text(CatalogText.imageSearchHint()) },
          leadingIcon = { Ic(Icons.Outlined.Search) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          onSubmit = { found.singleOrNull()?.let(onPick) }
        )
        if (found.isEmpty()) EmptyHint(CatalogText.nothing())
        LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          items(found, key = { it.id }) { e ->
            Row(
              Modifier.fillMaxWidth().interactive(MaterialTheme.shapes.small, bleed = 4.dp) {
                onPick(e)
              },
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              ExerciseThumb(e, 48.dp)
              Column(Modifier.weight(1f)) {
                Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                e.equipment?.let {
                  Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text(CatalogText.cancel()) } }
  )
}
