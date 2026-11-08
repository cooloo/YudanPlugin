"""验证真实类文件的类型引用与成员访问兼容性。"""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
from class_abi import parse_class

spec = importlib.util.spec_from_file_location('checker', Path(__file__).parents[1] / 'check-host-abi.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class AbiTest(unittest.TestCase):
    def test_music_implementation_is_plugin_owned_but_sdk_is_host_owned(self):
        self.assertFalse('com/egogame/music/plugin/common/StatefulMusicPlugin'.startswith(checker.SHARED))
        self.assertFalse('com/paopao/music/nowplaying/NowPlayingScreenKt'.startswith(checker.SHARED))
        self.assertTrue('com/egogame/plugin/api/YdPlugin'.startswith(checker.SHARED))
        self.assertTrue('androidx/media3/common/Player'.startswith(checker.SHARED))

    def test_member_names_are_not_descriptors(self):
        root = Path(__file__).parents[2] / 'build/abi-tests'
        root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root) as temporary:
            source = Path(temporary) / 'Links.java'
            source.write_text('public class Links { public String Label; public String Load() { return Label; } }', encoding='utf-8')
            javac = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17')) / 'bin/javac.exe'
            subprocess.run([str(javac), '-source', '8', '-target', '8', str(source)], check=True, capture_output=True)
            _, _, classes, _ = parse_class(source.with_suffix('.class').read_bytes())
            self.assertEqual({'java/lang/String', 'java/lang/Object', 'Links'}, classes)

    def test_public_widening_preserves_protected_member_access(self):
        self.assertTrue(checker.access_compatible(0x11, 0x14))

    def test_static_and_access_narrowing_remain_incompatible(self):
        self.assertFalse(checker.access_compatible(0x09, 0x01))
        self.assertFalse(checker.access_compatible(0x04, 0x01))

    def test_desugared_interface_requires_concrete_implementation(self):
        classes = {
            'Listener': {'access': 0x601, 'super': None, 'interfaces': [], 'methods': {'onEvents()V': 0x401}},
            'Plugin': {'access': 1, 'super': None, 'interfaces': ['Listener'], 'methods': {}},
        }
        self.assertEqual(['onEvents()V'], checker.missing_implementations(classes, 'Plugin'))
        classes['Adapter'] = {'access': 1, 'super': None, 'interfaces': ['Listener'], 'methods': {'onEvents()V': 1}}
        classes['Plugin']['super'] = 'Adapter'
        self.assertEqual([], checker.missing_implementations(classes, 'Plugin'))


if __name__ == '__main__':
    unittest.main()
