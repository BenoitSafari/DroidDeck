package com.droiddeck.launcher.frontend

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopModeTest {
    private val exe = "/root/Games/Windows/ff9/x64/FF9.exe"

    @Test
    fun onlyAddedGamesWithAnExecutableCanRunWithoutTheClient() {
        val added = Library.SteamGame(0x8DBA661A.toInt(), "ff9", null, Library.ADDED, gameId = -8234156706804793344L, guestExe = exe)
        assertTrue(DesktopMode.available(added))
        assertFalse(DesktopMode.available(Library.SteamGame(8400, "Geometry Wars", null, "steam", guestExe = exe)))
        // An added game the client owns (routed to its Steam appid) carries no executable.
        assertFalse(DesktopMode.available(Library.SteamGame(8400, "Geometry Wars", null, Library.ADDED)))
    }

    @Test
    fun theLauncherGetsTheShortcutPrefixAndTheExecutable() {
        val added = Library.SteamGame(0x8DBA661A.toInt(), "ff9", null, Library.ADDED, gameId = -8234156706804793344L, guestExe = exe)
        assertArrayEquals(arrayOf("2377803290", exe), DesktopMode.launchArgs(added))
        assertArrayEquals(
            arrayOf("--start-in=/root/Games/Windows/ff9", "--launch-options=-force-gfx-direct", "2377803290", exe),
            DesktopMode.launchArgs(added, "-force-gfx-direct", "/root/Games/Windows/ff9"),
        )
        assertNull(DesktopMode.launchArgs(Library.SteamGame(8400, "Geometry Wars", null, "steam")))
    }
}
