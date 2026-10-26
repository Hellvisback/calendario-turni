package com.turni.calendario

import com.turni.calendario.ui.theme.ThemeMode
import com.turni.calendario.ui.theme.resolveThemeMode
import org.junit.Assert.*
import org.junit.Test

class ThemeModeTest {
    @Test fun newInstallFollowsSystem(){assertEquals(ThemeMode.AUTOMATIC,resolveThemeMode(null,null))}
    @Test fun legacyExplicitChoicesArePreserved(){assertEquals(ThemeMode.DARK,resolveThemeMode(null,true));assertEquals(ThemeMode.LIGHT,resolveThemeMode(null,false))}
    @Test fun automaticModeFollowsBothSystemValues(){assertFalse(ThemeMode.AUTOMATIC.isDark(false));assertTrue(ThemeMode.AUTOMATIC.isDark(true))}
    @Test fun explicitChoicesIgnoreSystem(){assertTrue(ThemeMode.DARK.isDark(false));assertFalse(ThemeMode.LIGHT.isDark(true))}
    @Test fun savedModeTakesPriorityOverLegacyFlag(){assertEquals(ThemeMode.AUTOMATIC,resolveThemeMode("AUTOMATIC",true));assertEquals(ThemeMode.AUTOMATIC,resolveThemeMode("invalid",false))}
}
