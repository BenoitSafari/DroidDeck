package com.droiddeck.launcher.frontend

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AddedGamesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun steamInstallsInALibraryAreNotScannedAsAddedGames() {
        val library = tmp.newFolder("card")
        val common = File(library, "steamapps/common").apply { mkdirs() }
        File(library, "steamapps/appmanifest_2852190.acf").writeText(
            "\"AppState\"\n{\n\t\"appid\"\t\t\"2852190\"\n\t\"installdir\"\t\t\"MONSTER_HUNTER_STORIES_3_TWISTED_REFLECTION\"\n}\n",
        )
        File(library, "steamapps/appmanifest_17410.acf").writeText(
            "\"AppState\"\n{\n\t\"appid\"\t\t\"17410\"\n\t\"installdir\"\t\t\"Mirrors Edge\"\n}\n",
        )
        assertEquals(setOf("monster_hunter_stories_3_twisted_reflection", "mirrors edge"), AddedGames.steamInstallDirs(common))
        // Loose game folders at the library root are never Steam's.
        assertEquals(emptySet<String>(), AddedGames.steamInstallDirs(library))
    }

    @Test fun aGameNamedCrashIsNotMistakenForACrashHandler() {
        val game = tmp.newFolder("crash_bandicoot_nsane_trilogy")
        val exe = File(game, "CrashBandicootNSaneTrilogy.exe").apply { writeText("x") }
        File(game, "UnityCrashHandler64.exe").writeText("x")
        File(game, "CrashReportClient.exe").writeText("x")
        assertEquals(listOf(exe), AddedGames.candidates(game))
    }

    @Test fun anUnrealGameWithoutItsLauncherIsFoundInBinaries() {
        val game = tmp.newFolder("crash_bandicoot_4")
        val exe = File(game, "Lava/Binaries/Win64/CrashBandicoot4.exe").apply { parentFile!!.mkdirs(); writeText("x") }
        File(game, "Engine/Binaries/ThirdParty").mkdirs()
        assertEquals(listOf(exe), AddedGames.candidates(game))
    }
}
