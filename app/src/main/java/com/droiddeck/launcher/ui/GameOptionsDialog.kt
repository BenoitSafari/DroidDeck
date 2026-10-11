package com.droiddeck.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.frontend.AddedGames
import com.droiddeck.launcher.frontend.DesktopMode
import com.droiddeck.launcher.frontend.Library
import com.droiddeck.launcher.session.SessionPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Game options, from an added game's launch settings: which executable it starts, and - for
 * Desktop Mode, where there is no client to edit the shortcut in - its launch options and whether
 * it starts in the game's folder.
 */
@Composable
internal fun GameOptionsDialog(
    game: Library.SteamGame, folder: File,
    onExe: (File, String) -> Unit, onPickExe: (File) -> Unit, onClose: () -> Unit,
) {
    val context = LocalContext.current
    val shown = rememberShown(onClose)
    val close = { shown.targetState = false }
    var candidates by remember(folder) { mutableStateOf(emptyList<File>()) }
    // What the game starts now: the pick, else the first candidate - as AddedGames.scan decides.
    var exe by remember(folder) { mutableStateOf(SessionPrefs.addedGameExe(context, folder.path)) }
    var options by remember(game.gameId) { mutableStateOf(DesktopMode.launchOptions(context, game).orEmpty()) }
    var gameFolder by remember(game.gameId) { mutableStateOf(DesktopMode.startInGameFolder(context, game)) }
    LaunchedEffect(folder) {
        val found = withContext(Dispatchers.IO) { AddedGames.candidates(folder) }
        candidates = found
        if (exe.isEmpty()) exe = found.firstOrNull()?.path.orEmpty()
    }
    // Done takes the first focus: left to itself it lands in the text field and raises the keyboard
    // over the card.
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(30) {
            withFrameNanos { }
            if (runCatching { doneFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    val listed = (candidates.map { it.path } + exe).filter { it.isNotEmpty() }.distinct()

    AppDialog(shown, close, "gameOptions", wide = false) {
        DialogHeader(game.name, stringResource(R.string.game_options_title))
        // The card scrolls as a whole (AppDialog), so the sections are laid out at their own height.
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            OptionsSection(stringResource(R.string.game_options_exe))
            Column(Modifier.fillMaxWidth().clip(Shape14).background(MaterialTheme.colorScheme.surface).border(1.dp, LocalPalette.current.line, Shape14)) {
                for (path in listed) ExeRow(path.removePrefix(folder.path + "/"), path == exe) {
                    exe = path
                    onExe(folder, path)
                }
            }
            SecondaryButton(stringResource(R.string.mode_added_choose), compact = true) { close(); onPickExe(folder) }

            OptionsSection(stringResource(R.string.game_options_desktop_mode))
            AdbTextField(
                options, { options = it; DesktopMode.setLaunchOptions(context, game, it) },
                stringResource(R.string.game_options_launch), KeyboardType.Uri, ImeAction.Done,
                placeholder = stringResource(R.string.game_options_launch_hint), compact = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.game_options_game_folder), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Small(stringResource(R.string.game_options_game_folder_hint))
                }
                ToggleSwitch(gameFolder, label = stringResource(R.string.game_options_game_folder)) { on ->
                    gameFolder = on
                    DesktopMode.setStartInGameFolder(context, game, on)
                }
            }
            Small(stringResource(R.string.game_options_note))
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(stringResource(R.string.game_env_done), modifier = Modifier.focusRequester(doneFocus), onClick = close)
        }
    }
}

@Composable
private fun OptionsSection(title: String) =
    Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun ExeRow(name: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().background(if (selected) pal.signal.copy(alpha = 0.12f) else colors.surface)
            .clickable(role = Role.RadioButton, onClick = onClick).controllerConfirm(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(if (selected) "●" else "○", fontSize = 14.sp, color = if (selected) pal.signal else colors.onSurfaceVariant)
        Text(name, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = colors.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
