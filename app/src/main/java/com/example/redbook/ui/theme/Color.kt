package com.example.redbook.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val primaryLight = Color(0xFFFF2741)
val onPrimaryLight = Color(0xFFFFFFFF)
val secondaryLight = Color(0xFFE80535)
val onSecondaryLight = Color(0xFFFFFFFF)
val errorLight = Color(0xFFFF0000)
val onErrorLight = Color(0xFFFFFFFF)
val backgroundLight = Color(0xFFFAFAFA)
val surfaceLight = Color(0xFFFFFFFF)
val onSurfaceLight = Color(0xFF000000)
val surfaceVariantLight = Color(0xFFF4F4F5)//输入框提示文字
val outlineLight = Color(0xFFCCCCCC)//间隔框灰色
val onSurfaceSecondaryLight = Color(0xFF949494)//灰色正常字
val onSurfaceTertiaryLight = Color(0xFF525252)//深灰色小字
val tagsLight = Color(0xFF004EAE)//话题色
val redBackgroundLight = Color(0xFFFFE0E1)
val simileFillLight = Color(0xFFFFA958)
val starFillLight = Color(0xFFFFDD00)
val blueBackgroundLight = Color(0xFFE8EFFD)
val blueFillLight = Color(0xFF4C84F6)
val greenBackgroundLight = Color(0xFFEAFAF3)
val greenFillLight = Color(0xFF6CD69D)
val femaleLight = Color(0xFFFF0099)





val primaryDark = Color(0xFFFF7A8F)
val onPrimaryDark = Color(0xFF41000A)
val secondaryDark = Color(0xFF930021)
val onSecondaryDark = Color(0xFFFFE6EA)
val errorDark = Color(0xFFFF7A7A)
val onErrorDark = Color(0xFF410000)
val backgroundDark = Color(0xFF1C1B1F)
val surfaceDark = Color(0xFF2C2C2C)

val onSurfaceDark = Color(0xFFE3E1E6)
val onSurfaceVariantDark = Color(0xFFCAC4D0)
val surfaceVariantDark = Color(0xFF3B3B3E)
val outlineDark = Color(0xFF938F99)
val onSurfaceSecondaryDark = Color(0xFFD3D1D6)
val onSurfaceTertiaryDark = Color(0xFF9B9B9B)
val tagsDark = Color(0xFF6B9DFF)
val redBackgroundDark = Color(0xFF4C1A1C)
val simileFillDark = Color(0xFFFFC285)
val starFillDark = Color(0xFFFFE966)
val blueBackgroundDark = Color(0xFF2D3548)
val blueFillDark = Color(0xFF8BB3FF)
val greenBackgroundDark = Color(0xFF2D453D)
val greenFillDark = Color(0xFFA8E8C2)
val femaleDark = Color(0xFFFF78C2)


@Composable
fun getOutline(): Color =
    if (isSystemInDarkTheme()) outlineDark else outlineLight
@Composable
fun getOnSurfaceSecondary(): Color =
    if (isSystemInDarkTheme()) onSurfaceSecondaryDark else onSurfaceSecondaryLight

@Composable
fun getOnSurfaceTertiary(): Color =
    if (isSystemInDarkTheme()) onSurfaceTertiaryDark else onSurfaceTertiaryLight

@Composable
fun getTags(): Color =
    if (isSystemInDarkTheme()) tagsDark else tagsLight

@Composable
fun getRedBackground(): Color =
    if (isSystemInDarkTheme()) redBackgroundDark else redBackgroundLight

@Composable
fun getSimileFill(): Color =
    if (isSystemInDarkTheme()) simileFillDark else simileFillLight

@Composable
fun getStarFill(): Color =
    if (isSystemInDarkTheme()) starFillDark else starFillLight

@Composable
fun getBlueBackground(): Color =
    if (isSystemInDarkTheme()) blueBackgroundDark else blueBackgroundLight

@Composable
fun getBlueFill(): Color =
    if (isSystemInDarkTheme()) blueFillDark else blueFillLight

@Composable
fun getGreenBackground(): Color =
    if (isSystemInDarkTheme()) greenBackgroundDark else greenBackgroundLight

@Composable
fun getGreenFill(): Color =
    if (isSystemInDarkTheme()) greenFillDark else greenFillLight

@Composable
fun getFemale(): Color =
    if (isSystemInDarkTheme()) femaleDark else femaleLight