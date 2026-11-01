package dev.akkraizen.berill.plugins.nutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.animation.AppearAnimated
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.FieldWithActions
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.api.ui.nowMillis
import dev.akkraizen.berill.api.ui.parseAmount
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.state.rememberDraft
import dev.akkraizen.berill.api.ui.toDateOrNull
import dev.akkraizen.berill.plugins.nutrition.data.MEALS
import dev.akkraizen.berill.plugins.nutrition.data.MEAL_SNACK
import dev.akkraizen.berill.plugins.nutrition.data.OpenFoodFacts
import dev.akkraizen.berill.plugins.nutrition.data.Product
import dev.akkraizen.berill.plugins.nutrition.data.foods
import dev.akkraizen.berill.plugins.nutrition.data.formatNum
import dev.akkraizen.berill.plugins.nutrition.data.kcalText
import dev.akkraizen.berill.plugins.nutrition.data.toProduct
import dev.akkraizen.berill.plugins.nutrition.i18n.NutritionText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AddFoodScreen(ctx: PluginContext, date: String, initialMeal: String, onError: (Exception) -> Unit = { throw it }, header: (@Composable () -> Unit)? = null,
  onCreated: ((Product, String, Double, Boolean) -> Unit)? = null) {
  var meal by
    rememberSaveable(initialMeal) {
      mutableStateOf(initialMeal.takeIf { it in MEALS } ?: MEAL_SNACK)
    }
  val quickDraft = LocalFoodDraft.current
  var localPending by remember { mutableStateOf<Product?>(null) }
  var portionHidden by remember { mutableStateOf(false) }
  val pending = if (quickDraft != null) quickDraft.value.pending?.toProduct() else localPending
  fun pick(product: Product?) {
    portionHidden = false
    if (quickDraft == null) localPending = product
    else quickDraft.value = quickDraft.value.copy(pending = product?.toEntry(date, meal, product.defaultGrams, 0), inputs = quickDraft.value.inputs - FoodInput.GRAMS)
  }
  fun save(product: Product, grams: Double, again: Boolean) {
    try {
      if (onCreated != null) onCreated(product, meal, grams, again)
      else {
        collectionSave(ctx, product, date, meal, grams)
        pick(null)
        ctx.nav.back()
      }
    } catch (e: CancellationException) { throw e }
      catch (e: Exception) { onError(e) }
  }
  val dayTitle = date.toDateOrNull()?.dayTitle() ?: date

  PluginList {
    if (header != null) item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { header() } }
    if (pending != null && portionHidden) item {
      FilledTonalButton(onClick = { portionHidden = false }) { Text(pending.name) }
    }
    item {
      DetailHeader(NutritionText.addTitle(NutritionText.mealLabel(meal), dayTitle)(), Icons.Outlined.Restaurant)
    }
    item { ChoiceChips(MEALS, meal, { NutritionText.mealLabel(it) }, { meal = it; quickDraft?.let { draft -> draft.value = draft.value.copy(meal = it) } }) }
    item {
      TwoPane(
        left = {
          SearchCard(ctx) { pick(it) }
          BarcodeCard(ctx) { pick(it) }
        },
        right = {
          RecentCard(ctx) { pick(it) }
          ManualCard { pick(it) }
        }
      )
    }
  }

  pending?.takeUnless { portionHidden }?.let { product ->
    PortionDialog(
      product = product,
      onDismiss = { if (quickDraft != null) portionHidden = true else pick(null) },
      onAdd = { grams -> save(product, grams, false) },
      onAddMore = if (onCreated != null) ({ grams -> save(product, grams, true) }) else null,
      enabled = date.toDateOrNull() != null
    )
  }
}

@Composable
private fun SearchCard(ctx: PluginContext, onPick: (Product) -> Unit) {
  val api = remember(ctx) { OpenFoodFacts(ctx) }
  val scope = rememberCoroutineScope()
  var query by rememberFoodField(FoodInput.SEARCH, "nutrition/add/search")
  var loading by remember { mutableStateOf(false) }
  var results by remember { mutableStateOf<List<Product>>(emptyList()) }
  var message by remember { mutableStateOf<String?>(null) }

  fun search() {
    val q = query.trim()
    if (q.isEmpty() || loading) return
    loading = true
    message = null
    scope.launch {
      try {
        results = api.search(q)
        if (results.isEmpty()) message = UiText.nothingFound()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        results = emptyList()
        message = OpenFoodFacts.errorText(e)
      } finally {
        loading = false
      }
    }
  }

  SectionCard(NutritionText.searchTitle(), Icons.Outlined.Search) {
    FieldWithActions(
      field = {
        BerillTextField(
          query,
          { query = it },
          placeholder = { Text(NutritionText.searchPlaceholder(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          modifier = Modifier.fillMaxWidth(),
          onSubmit = ::search
        )
      },
      actions = {
        Button(onClick = ::search, enabled = !loading && query.isNotBlank()) {
          if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
          }
          Text(UiText.find(), modifier = Modifier.padding(start = 8.dp))
        }
      }
    )
    message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (results.isNotEmpty()) {
      Text(
        NutritionText.searchHint(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    if (results.isNotEmpty()) dev.akkraizen.berill.api.ui.layout.BoundedList {
      results.forEach { product ->
        key(product.code ?: product.name) {
          AppearAnimated { ProductRow(product, showImage = true) { onPick(product) } }
        }
      }
    }

  }
}

@Composable
private fun ProductRow(product: Product, showImage: Boolean, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth()
      .interactive(MaterialTheme.shapes.small, bleed = 6.dp, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    if (showImage)
      Cover(product.image, Icons.Outlined.Restaurant, Modifier.size(44.dp), shape = MaterialTheme.shapes.small)
    Column(Modifier.weight(1f)) {
      Text(product.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(
        listOfNotNull(product.brand, macroText(product.protein100, product.fat100, product.carbs100))
          .joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Column(horizontalAlignment = Alignment.End) {
      Text(product.kcal100.kcalText(), style = MaterialTheme.typography.titleSmall)
      Text(
        NutritionText.per100(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun BarcodeCard(ctx: PluginContext, onPick: (Product) -> Unit) {
  val api = remember(ctx) { OpenFoodFacts(ctx) }
  val scope = rememberCoroutineScope()
  var code by rememberFoodField(FoodInput.BARCODE, "nutrition/add/barcode")
  var loading by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }

  fun search() {
    val c = code.trim()
    if (c.length < 6 || loading) return
    loading = true
    message = null
    scope.launch {
      try {
        val product = api.byBarcode(c)
        if (product == null) message = NutritionText.barcodeNotFound() else onPick(product)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        message = OpenFoodFacts.errorText(e)
      } finally {
        loading = false
      }
    }
  }

  SectionCard(NutritionText.barcodeTitle(), Icons.Outlined.QrCodeScanner) {
    FieldWithActions(
      field = {
        BerillTextField(
          code,
          { code = it.filter(Char::isDigit).take(14) },
          label = { Text(NutritionText.barcode(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
          modifier = Modifier.fillMaxWidth(),
          onSubmit = ::search
        )
      },
      actions = {
        FilledTonalButton(onClick = ::search, enabled = !loading && code.length >= 6) {
          if (loading)
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
          else
            Text(UiText.find())
        }
      }
    )
    message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
  }
}

@Composable
private fun RecentCard(ctx: PluginContext, onPick: (Product) -> Unit) {
  val collection = remember(ctx) { foods(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  val recent = all.map { it.value }
    .sortedByDescending { it.createdAt }
    .distinctBy { "${it.name.lowercase()}|${it.brand?.lowercase()}" }
    .take(12)
  SectionCard(NutritionText.recentTitle(), Icons.Outlined.History) {
    if (recent.isEmpty()) EmptyHint(NutritionText.noRecent())
    if (recent.isNotEmpty()) dev.akkraizen.berill.api.ui.layout.BoundedList {
      recent.forEach { e ->
        key(e.name, e.brand) {
          ProductRow(e.toProduct(), showImage = false) { onPick(e.toProduct()) }
        }
      }
    }

  }
}

@Composable
private fun ManualCard(onPick: (Product) -> Unit) {
  var name by rememberFoodField(FoodInput.NAME, "nutrition/add/manual/name")
  var kcal by rememberFoodField(FoodInput.KCAL, "nutrition/add/manual/kcal")
  var protein by rememberFoodField(FoodInput.PROTEIN, "nutrition/add/manual/protein")
  var fat by rememberFoodField(FoodInput.FAT, "nutrition/add/manual/fat")
  var carbs by rememberFoodField(FoodInput.CARBS, "nutrition/add/manual/carbs")
  val k = parseAmount(kcal)?.takeIf { it >= 0 }
  val valid = name.isNotBlank() && k != null
  fun submit() {
    if (!valid) return
    onPick(
      Product(
        name = name.trim(),
        kcal100 = k!!,
        protein100 = parseAmount(protein) ?: 0.0,
        fat100 = parseAmount(fat) ?: 0.0,
        carbs100 = parseAmount(carbs) ?: 0.0
      )
    )
    name = ""
    kcal = ""
    protein = ""
    fat = ""
    carbs = ""
  }

  SectionCard(NutritionText.manualTitle(), Icons.Outlined.EditNote) {
    BerillTextField(
      name,
      { name = it },
      label = { Text(NutritionText.name(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      onSubmit = ::submit
    )
    BerillTextField(
      kcal,
      { kcal = it.take(7) },
      label = { Text(NutritionText.kcalPer100(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
      singleLine = true,
      isError = kcal.isNotBlank() && k == null,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      modifier = Modifier.fillMaxWidth(),
      onSubmit = ::submit
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(
        Triple(protein, NutritionText.proteinPer100()) { v: String -> protein = v },
        Triple(fat, NutritionText.fatPer100()) { v: String -> fat = v },
        Triple(carbs, NutritionText.carbsPer100()) { v: String -> carbs = v }
      )
        .forEach { (value, label, set) ->
          BerillTextField(
            value,
            { set(it.take(6)) },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            isError = value.isNotBlank() && parseAmount(value) == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            onSubmit = ::submit
          )
        }
    }
    FilledTonalButton(onClick = ::submit, enabled = valid) { Text(NutritionText.add()) }
  }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PortionDialog(product: Product, onDismiss: () -> Unit, onAdd: (Double) -> Unit, onAddMore: ((Double) -> Unit)? = null, enabled: Boolean = true) {
  var grams by if (LocalFoodDraft.current != null)
    rememberFoodField(FoodInput.GRAMS, "nutrition/add/portion", formatNum(product.defaultGrams))
  else remember(product) { mutableStateOf(formatNum(product.defaultGrams)) }
  val g = parseAmount(grams)?.takeIf { it > 0 }
  val confirm: () -> Unit = { if (g != null && enabled) onAdd(g) }
  DialogKeys(onConfirm = confirm, confirmEnabled = g != null && enabled, onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(product.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        product.brand?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
          "${NutritionText.kcalValue(product.kcal100.kcalText())()} ${NutritionText.per100()} · " +
            macroText(product.protein100, product.fat100, product.carbs100),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BerillTextField(
          grams,
          { grams = it.take(7) },
          label = { Text(NutritionText.grams(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          singleLine = true,
          isError = g == null,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          product.servingGrams?.let { s ->
            BerillChip(onClick = { grams = formatNum(s) }, label = { Text(NutritionText.serving(formatNum(s))(), maxLines = 1, overflow = TextOverflow.Ellipsis) })
          }
          listOf(50.0, 100.0, 200.0).forEach { v ->
            BerillChip(onClick = { grams = formatNum(v) }, label = { Text(formatNum(v), maxLines = 1, overflow = TextOverflow.Ellipsis) })
          }
        }
        val p = g ?: 0.0
        Text(
          NutritionText.kcalValue((product.kcal100 * p / 100).kcalText())(),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.primary
        )
        Text(macroText(product.protein100 * p / 100, product.fat100 * p / 100, product.carbs100 * p / 100))
      }
    },
    confirmButton = {
      androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.End) {
        if (onAddMore != null) TextButton(enabled = g != null && enabled, onClick = { g?.let(onAddMore) }) { Text(dev.akkraizen.berill.api.i18n.I18n.text("quick.saveMore")) }
        TextButton(onClick = confirm, enabled = g != null && enabled) { Text(NutritionText.add()) }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(NutritionText.cancel()) } }
  )
}

private fun collectionSave(ctx: PluginContext, product: Product, date: String, meal: String, grams: Double) {
  foods(ctx).save(ctx.data.newId(), product.toEntry(date, meal, grams, nowMillis()))
}
