package tj.umed.drivedashboard

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Xiaomi MiSans Latin, downloaded from the official site during CI build. */
val MiSansFamily = FontFamily(
    Font(R.font.misans_latin_thin, FontWeight.Thin),
    Font(R.font.misans_latin_extralight, FontWeight.ExtraLight),
    Font(R.font.misans_latin_light, FontWeight.Light),
    Font(R.font.misans_latin_regular, FontWeight.Normal),
    Font(R.font.misans_latin_medium, FontWeight.Medium),
    Font(R.font.misans_latin_semibold, FontWeight.SemiBold),
    Font(R.font.misans_latin_demibold, FontWeight(650)),
    Font(R.font.misans_latin_bold, FontWeight.Bold),
    Font(R.font.misans_latin_heavy, FontWeight.Black)
)

private val baseTypography = Typography()
val MiSansTypography = Typography(
    displayLarge = baseTypography.displayLarge.copy(fontFamily = MiSansFamily),
    displayMedium = baseTypography.displayMedium.copy(fontFamily = MiSansFamily),
    displaySmall = baseTypography.displaySmall.copy(fontFamily = MiSansFamily),
    headlineLarge = baseTypography.headlineLarge.copy(fontFamily = MiSansFamily),
    headlineMedium = baseTypography.headlineMedium.copy(fontFamily = MiSansFamily),
    headlineSmall = baseTypography.headlineSmall.copy(fontFamily = MiSansFamily),
    titleLarge = baseTypography.titleLarge.copy(fontFamily = MiSansFamily),
    titleMedium = baseTypography.titleMedium.copy(fontFamily = MiSansFamily),
    titleSmall = baseTypography.titleSmall.copy(fontFamily = MiSansFamily),
    bodyLarge = baseTypography.bodyLarge.copy(fontFamily = MiSansFamily),
    bodyMedium = baseTypography.bodyMedium.copy(fontFamily = MiSansFamily),
    bodySmall = baseTypography.bodySmall.copy(fontFamily = MiSansFamily),
    labelLarge = baseTypography.labelLarge.copy(fontFamily = MiSansFamily),
    labelMedium = baseTypography.labelMedium.copy(fontFamily = MiSansFamily),
    labelSmall = baseTypography.labelSmall.copy(fontFamily = MiSansFamily)
)
