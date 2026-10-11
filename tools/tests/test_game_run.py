import importlib.machinery
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

BIN = Path(__file__).resolve().parents[1] / 'linuxfs/overlay/usr/local/bin'


def load(name):
    loader = importlib.machinery.SourceFileLoader(name, str(BIN / name))
    spec = importlib.util.spec_from_loader(name, loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module


game_run = load('droiddeck-game-run')
shortcuts = load('droiddeck-steam-shortcuts')

APPID = 0x8DBA661A
TOOL = 'droiddeck-proton-arm64'


class GameRunTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)
        self.steam = self.home / '.local/share/Steam'
        self.tools = self.steam / 'compatibilitytools.d'
        self.game = self.home / 'Games/Example'
        (self.game / 'bin').mkdir(parents=True)
        self.exe = self.game / 'bin/Example.exe'
        self.exe.write_bytes(b'MZ')
        self.env = {'HOME': str(self.home), 'PATH': os.environ['PATH']}

    def depot(self):
        (self.steam / 'steamapps/common/Proton Experimental (ARM64)/files/bin-arm64').mkdir(parents=True, exist_ok=True)

    def tool(self, folder, name='droiddeck-proton', depot=True):
        if folder == TOOL and depot:
            self.depot()
        path = self.tools / folder / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('#!/bin/sh\nprintf "%s\\n" "$PWD" "$STEAM_COMPAT_DATA_PATH" "$SteamAppId$SteamGameId" "${EXTRA:-}" "$@"\n')
        path.chmod(0o755)
        return str(path)

    def state(self, **state):
        path = self.home / '.local/share/droiddeck-compat/state.json'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(state))

    def shortcut(self, **fields):
        config = self.steam / 'userdata/123/config'
        config.mkdir(parents=True, exist_ok=True)
        entry = shortcuts.shortcut(dict(appid=APPID, name='Example', exe=str(self.exe), dir=str(self.exe.parent)))
        entry.update(fields)
        (config / 'shortcuts.vdf').write_bytes(shortcuts.write({'shortcuts': {'0': entry}}))

    def run_game(self, *args):
        return subprocess.run([sys.executable, str(BIN / 'droiddeck-game-run'), *args],
                              env=self.env, capture_output=True, text=True)

    def test_launch_options_split_around_command(self):
        self.assertEqual(game_run.launch_options('-windowed -w 1280'), ({}, ['-windowed', '-w', '1280']))
        self.assertEqual(game_run.launch_options('DXVK_HUD=fps WINEDLLOVERRIDES="dinput8=n,b" %command% -skip'),
                         ({'DXVK_HUD': 'fps', 'WINEDLLOVERRIDES': 'dinput8=n,b'}, ['-skip']))
        self.assertEqual(game_run.launch_options('gamemoderun %command%'), ({}, []))
        self.assertEqual(game_run.launch_options('"unbalanced'), ({}, []))
        self.assertEqual(game_run.launch_options(None), ({}, []))

    def test_launcher_follows_the_default_tool(self):
        self.assertIsNone(game_run.launcher(str(self.steam), self.env))
        # Registered before the client has fetched the depot: nothing to run yet.
        own = self.tool(TOOL, depot=False)
        self.assertIsNone(game_run.launcher(str(self.steam), self.env))
        extra = self.tool('GE-Proton11-7', 'droiddeck-proton-wrap')
        self.assertEqual(game_run.launcher(str(self.steam), self.env), extra)
        self.depot()
        self.assertEqual(game_run.launcher(str(self.steam), self.env), own)
        self.state(default='GE-Proton11-7-arm64', tools=[
            dict(name=TOOL, dir='Proton Experimental (ARM64)', valve=True),
            dict(name='GE-Proton11-7-arm64', dir='GE-Proton11-7', valve=False)])
        self.assertEqual(game_run.launcher(str(self.steam), self.env), extra)
        self.state(default='gone', tools=[])
        self.assertEqual(game_run.launcher(str(self.steam), self.env), own)

    def test_a_pick_in_components_wins_without_a_client(self):
        own = self.tool(TOOL)
        ge = self.tool('GE-Proton11-7', 'droiddeck-proton-wrap')
        cachy = self.tool('proton-cachyos-11', 'droiddeck-proton-wrap')
        request = self.home / '.local/share/droiddeck-compat/request.json'
        request.parent.mkdir(parents=True, exist_ok=True)
        request.write_text(json.dumps(dict(seq=5, valve=False, dir='GE-Proton11-7')))
        self.assertEqual(game_run.launcher(str(self.steam), self.env), ge)
        request.write_text(json.dumps(dict(seq=6, valve=True, dir='Proton Experimental (ARM64)')))
        self.assertEqual(game_run.launcher(str(self.steam), self.env), own)
        # Applied by the client since: its default is what counts again.
        self.state(default='proton-cachyos', applied=6, tools=[dict(name='proton-cachyos', dir='proton-cachyos-11', valve=False)])
        self.assertEqual(game_run.launcher(str(self.steam), self.env), cachy)

    def test_runs_the_launch_verb_in_the_shortcut_prefix(self):
        self.tool(TOOL)
        done = self.run_game(str(APPID), str(self.exe), '-first')
        self.assertEqual(done.returncode, 0, done.stderr)
        prefix = str(self.steam / 'steamapps/compatdata' / str(APPID))
        self.assertEqual(done.stdout.splitlines(),
                         [str(self.exe.parent), prefix, str(APPID) * 2, '', 'waitforexitandrun', str(self.exe), '-first'])
        self.assertTrue(os.path.isdir(prefix))

    def test_start_dir_and_launch_options_come_from_the_client(self):
        self.tool(TOOL)
        self.shortcut(StartDir='"%s"' % self.game, LaunchOptions='EXTRA=1 %command% -force-gfx-direct')
        done = self.run_game(str(APPID), str(self.exe))
        self.assertEqual(done.returncode, 0, done.stderr)
        lines = done.stdout.splitlines()
        self.assertEqual(lines[0], str(self.game))
        self.assertEqual(lines[3:], ['1', 'waitforexitandrun', str(self.exe), '-force-gfx-direct'])

    def test_the_games_own_options_win_over_the_clients(self):
        self.tool(TOOL)
        self.shortcut(StartDir='"%s"' % self.exe.parent, LaunchOptions='-from-steam')
        done = self.run_game('--start-in=%s' % self.game, '--launch-options=EXTRA=2 %command% -mine', str(APPID), str(self.exe))
        self.assertEqual(done.returncode, 0, done.stderr)
        lines = done.stdout.splitlines()
        self.assertEqual(lines[0], str(self.game))
        self.assertEqual(lines[3:], ['2', 'waitforexitandrun', str(self.exe), '-mine'])
        # Given but empty: no options at all, not the client's.
        done = self.run_game('--launch-options=', str(APPID), str(self.exe))
        self.assertEqual(done.stdout.splitlines()[3:], ['', 'waitforexitandrun', str(self.exe)])
        self.assertEqual(self.run_game('--nonsense=1', str(APPID), str(self.exe)).returncode, 64)

    def test_refuses_what_it_cannot_start(self):
        self.assertEqual(self.run_game('0', str(self.exe)).returncode, 64)
        self.assertEqual(self.run_game(str(2**32), str(self.exe)).returncode, 64)
        self.assertEqual(self.run_game(str(APPID), str(self.game / 'missing.exe')).returncode, 66)
        self.assertEqual(self.run_game(str(APPID), str(self.exe)).returncode, 69)


if __name__ == '__main__':
    unittest.main()
