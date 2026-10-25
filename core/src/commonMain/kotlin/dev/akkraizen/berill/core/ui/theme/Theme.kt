package dev.akkraizen.berill.core.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

private val Light = lightColorScheme(
  primary = Color(0xFF2E6B30),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFB1F2A8),
  onPrimaryContainer = Color(0xFF002204),
  secondary = Color(0xFF52634F),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFD5E8CF),
  onSecondaryContainer = Color(0xFF101F0F),
  tertiary = Color(0xFF39656B),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFBCEBF1),
  onTertiaryContainer = Color(0xFF001F23),
  background = Color(0xFFF7FBF1),
  onBackground = Color(0xFF181D17),
  surface = Color(0xFFF7FBF1),
  onSurface = Color(0xFF181D17),
  surfaceVariant = Color(0xFFDEE5D8),
  onSurfaceVariant = Color(0xFF424940),
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color(0xFFF1F5EC),
  surfaceContainer = Color(0xFFEBEFE6),
  surfaceContainerHigh = Color(0xFFE6E9E0),
  surfaceContainerHighest = Color(0xFFE0E4DB),
  outlineVariant = Color(0xFFC2C9BD)
)

private val Dark = darkColorScheme(
  primary = Color(0xFF96D78E),
  onPrimary = Color(0xFF003909),
  primaryContainer = Color(0xFF12521B),
  onPrimaryContainer = Color(0xFFB1F2A8),
  secondary = Color(0xFFB9CCB4),
  onSecondary = Color(0xFF253423),
  secondaryContainer = Color(0xFF3B4B39),
  onSecondaryContainer = Color(0xFFD5E8CF),
  tertiary = Color(0xFFA1CED5),
  onTertiary = Color(0xFF00363C),
  tertiaryContainer = Color(0xFF1F4D53),
  onTertiaryContainer = Color(0xFFBCEBF1),
  background = Color(0xFF10140F),
  onBackground = Color(0xFFE0E4DB),
  surface = Color(0xFF10140F),
  onSurface = Color(0xFFE0E4DB),
  surfaceVariant = Color(0xFF424940),
  onSurfaceVariant = Color(0xFFC2C9BD),
  surfaceContainerLowest = Color(0xFF0B0F0A),
  surfaceContainerLow = Color(0xFF181D17),
  surfaceContainer = Color(0xFF1C211B),
  surfaceContainerHigh = Color(0xFF272B25),
  surfaceContainerHighest = Color(0xFF323630),
  outlineVariant = Color(0xFF424940)
)

val BerillShapes = Shapes(
  extraSmall = RoundedCornerShape(4.dp),
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(12.dp),
  large = RoundedCornerShape(16.dp),
  extraLarge = RoundedCornerShape(20.dp)
)

private val Base = Typography()
private val BerillTypography = Base.copy(
  displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
  headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
  headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
  titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
  titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
  labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BerillTheme(appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
  val dark = when (appearance.mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
  }
  val dynamic = if (appearance.accent == Appearance.ACCENT_DYNAMIC) platformDynamicColorScheme(dark) else null
  val seed = parseHexColor(appearance.accent)
  val scheme = when {
    dynamic != null -> dynamic
    seed != null ->
      remember(seed, dark) {
        dynamicColorScheme(seedColor = seed, isDark = dark, style = PaletteStyle.TonalSpot)
      }

    else -> if (dark) Dark else Light
  }
  MaterialExpressiveTheme(
    colorScheme = animateColorScheme(scheme),
    motionScheme = MotionScheme.expressive(),
    shapes = BerillShapes,
    typography = BerillTypography,
    content = content
  )
}

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
  val spec = spring<Color>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

  @Composable
  fun animate(color: Color): Color = animateColorAsState(color, spec, label = "scheme").value

  return target.copy(
    primary = animate(target.primary),
    onPrimary = animate(target.onPrimary),
    primaryContainer = animate(target.primaryContainer),
    onPrimaryContainer = animate(target.onPrimaryContainer),
    inversePrimary = animate(target.inversePrimary),
    secondary = animate(target.secondary),
    onSecondary = animate(target.onSecondary),
    secondaryContainer = animate(target.secondaryContainer),
    onSecondaryContainer = animate(target.onSecondaryContainer),
    tertiary = animate(target.tertiary),
    onTertiary = animate(target.onTertiary),
    tertiaryContainer = animate(target.tertiaryContainer),
    onTertiaryContainer = animate(target.onTertiaryContainer),
    background = animate(target.background),
    onBackground = animate(target.onBackground),
    surface = animate(target.surface),
    onSurface = animate(target.onSurface),
    surfaceVariant = animate(target.surfaceVariant),
    onSurfaceVariant = animate(target.onSurfaceVariant),
    surfaceTint = animate(target.surfaceTint),
    inverseSurface = animate(target.inverseSurface),
    inverseOnSurface = animate(target.inverseOnSurface),
    error = animate(target.error),
    onError = animate(target.onError),
    errorContainer = animate(target.errorContainer),
    onErrorContainer = animate(target.onErrorContainer),
    outline = animate(target.outline),
    outlineVariant = animate(target.outlineVariant),
    surfaceBright = animate(target.surfaceBright),
    surfaceDim = animate(target.surfaceDim),
    surfaceContainer = animate(target.surfaceContainer),
    surfaceContainerHigh = animate(target.surfaceContainerHigh),
    surfaceContainerHighest = animate(target.surfaceContainerHighest),
    surfaceContainerLow = animate(target.surfaceContainerLow),
    surfaceContainerLowest = animate(target.surfaceContainerLowest)
  )
}
