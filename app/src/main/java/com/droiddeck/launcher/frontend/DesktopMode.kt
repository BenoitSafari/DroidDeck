package com.droiddeck.launcher.frontend

import android.content.Context

/**
 * Games > Desktop Mode: an added game started with its Proton under gamescope, without the Steam
 * client. The choice is per game and follows it everywhere it is launched from - the Games page, a
 * home-screen shortcut, a game link.
 */
object DesktopMode {
    const val LAUNCHER = "/usr/local/bin/droiddeck-game-run"
    private const val PREFS = "desktop-mode"
    private const val KEY_GAMES = "games"

    /** Only games added to the library: a title the client owns needs the client running. */
    fun available(game: Library.SteamGame): Boolean = game.library == Library.ADDED && game.guestExe != null

    fun enabled(context: Context, game: Library.SteamGame): Boolean = available(game) &&
        game.gameIdString in context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_GAMES, emptySet()).orEmpty()

    fun set(context: Context, game: Library.SteamGame, on: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val games = prefs.getStringSet(KEY_GAMES, emptySet()).orEmpty().toMutableSet()
        if (on) games.add(game.gameIdString) else games.remove(game.gameIdString)
        prefs.edit().putStringSet(KEY_GAMES, games).apply()
    }

    /** A Proton to run the game with: Valve's depot once the client has fetched it, or one from Components. */
    fun protonReady(context: Context): Boolean =
        runCatching { com.droiddeck.launcher.session.ComponentsManager.protons(context).isNotEmpty() }.getOrDefault(false)

    /** The shortcut's appid, the key of its prefix (compatdata/<id>). */
    fun prefixId(game: Library.SteamGame): String = Integer.toUnsignedString(game.appId)

    /**
     * Game options: the launch options as Steam writes them (`VAR=1 %command% -arg`, or arguments
     * alone). null when never set here - the client's own, if it has any, then apply.
     */
    fun launchOptions(context: Context, game: Library.SteamGame): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("options:" + game.gameIdString, null)

    fun setLaunchOptions(context: Context, game: Library.SteamGame, options: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("options:" + game.gameIdString, options.trim()).apply()

    /** Game options: start in the game's folder rather than beside the executable (Steam's default). */
    fun startInGameFolder(context: Context, game: Library.SteamGame): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("gameFolder:" + game.gameIdString, false)

    fun setStartInGameFolder(context: Context, game: Library.SteamGame, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("gameFolder:" + game.gameIdString, on).apply()

    /** [LAUNCHER]'s arguments from the choices above, or null for a game it cannot start. */
    fun launchArgs(game: Library.SteamGame, options: String? = null, startIn: String? = null): Array<String>? =
        game.guestExe?.takeIf { available(game) }?.let { exe ->
            (listOfNotNull(startIn?.let { "--start-in=$it" }, options?.let { "--launch-options=$it" }) + prefixId(game) + exe).toTypedArray()
        }

    fun launchArgs(context: Context, game: Library.SteamGame): Array<String>? = launchArgs(
        game, launchOptions(context, game),
        game.gameFiles?.takeIf { startInGameFolder(context, game) }?.let { AddedGames.guestPath(context, it) },
    )
}
