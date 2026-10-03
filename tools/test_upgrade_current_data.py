"""Behavioral checks for the offline current-data upgrade."""
import json
import tempfile
import unittest
from pathlib import Path
import upgrade_current_data as upgrade


class UpgradeTest(unittest.TestCase):
    def test_yacht_category_names_preserve_custom_translations(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / 'source'
            (source / 'languages').mkdir(parents=True)
            (source / 'languages/en_US.yml').write_text('"score.category.0": "Custom aces"\n"score.category.11": "Custom yacht"\n', encoding='utf-8')
            output = Path(temporary) / 'output'
            report = upgrade.convert(source, output)
            values = upgrade.yaml.safe_load((output / 'languages/en_US.yml').read_text(encoding='utf-8'))
            self.assertEqual('Custom aces', values['score.category.ones'])
            self.assertEqual('Custom yacht', values['score.category.yacht'])
            self.assertFalse(any(key.rsplit('.', 1)[-1].isdigit() for key in values if key.startswith('score.category.')))
            self.assertFalse(report['unmapped'])

    def test_current_card_history_and_custom_language_are_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / 'source'
            output = Path(temporary) / 'output'
            (source / 'languages').mkdir(parents=True)
            (source / 'config.yml').write_text('language: en\ncustom: keep\n', encoding='utf-8')
            (source / 'languages/en_US.yml').write_text('"menu.close": "My close"\n"unmapped.custom": "Keep this"\n', encoding='utf-8')
            room = {'kind': 'lastcard', 'rulesVersion': 2, 'id': 'same-id', 'seed': 123,
                    'options': {'finish': 'first'}, 'history': [{'seat': 0, 'action': 'draw'}]}
            (source / 'rooms.json').write_text(json.dumps({'schema': 1, 'rooms': [room], 'returns': {}}))
            original = (source / 'rooms.json').read_bytes()
            report = upgrade.convert(source, output)
            converted = json.loads((output / 'rooms.json').read_text())['rooms'][0]
            self.assertEqual('color-eight', converted['kind'])
            self.assertEqual(room['history'], converted['history'])
            self.assertEqual(123, converted['seed'])
            self.assertEqual({}, converted['options'])
            self.assertEqual(original, (source / 'rooms.json').read_bytes())
            language = upgrade.yaml.safe_load((output / 'languages/en_US.yml').read_text(encoding='utf-8'))
            self.assertEqual('My close', language['menu.close'])
            self.assertTrue(any(value['value'] == 'Keep this' for value in report['unmapped']))
            self.assertEqual('en_US', upgrade.yaml.safe_load((output / 'config.yml').read_text())['language'])
            again = Path(temporary) / 'again'
            upgrade.convert(output, again)
            self.assertEqual((output / 'rooms.json').read_bytes(), (again / 'rooms.json').read_bytes())
            self.assertEqual((output / 'languages/en_US.yml').read_bytes(), (again / 'languages/en_US.yml').read_bytes())

    def test_old_rules_are_rejected_without_partial_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / 'source'
            source.mkdir()
            (source / 'rooms.json').write_text(json.dumps({'rooms': [{'kind': 'lastcard', 'rulesVersion': 1}]}))
            output = Path(temporary) / 'output'
            with self.assertRaisesRegex(ValueError, 'rules version'):
                upgrade.convert(source, output)
            self.assertFalse(output.exists())

    def test_nested_messages_and_custom_menu_captions(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / 'source'
            (source / 'lang').mkdir(parents=True)
            (source / 'menus').mkdir()
            (source / 'lang/en.yml').write_text('messages:\n  menu:\n    close: Customized\ntranslations:\n  Special phrase: Special value\n', encoding='utf-8')
            historical_caption = '&f' + upgrade.MAPPING['defaults']['zh_CN']['menu.bots']
            (source / 'menus/room.yml').write_text(upgrade.dump({'Bottom': {'buttons': {
                'bots': {'text': historical_caption},
                'options': {'text': 'Custom options'}}}}), encoding='utf-8')
            output = Path(temporary) / 'output'
            report = upgrade.convert(source, output)
            values = upgrade.yaml.safe_load((output / 'languages/en_US.yml').read_text(encoding='utf-8'))
            self.assertEqual('Customized', values['menu.close'])
            menu = upgrade.yaml.safe_load((output / 'menus/room.yml').read_text(encoding='utf-8'))
            self.assertEqual('<white>@label@', menu['Bottom']['buttons']['bots']['text'])
            self.assertEqual('Custom options', menu['Bottom']['buttons']['options']['text'])
            self.assertTrue(any(item['value'] == 'Special value' for item in report['unmapped']))


if __name__ == '__main__':
    unittest.main()
