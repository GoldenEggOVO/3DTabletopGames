"""Prepare a separate 1.9 data directory. Never modify the input directory.

Requires Python 3.11+ and PyYAML. Stop the server, retain the original directory,
validate the output rooms with RoomReplayVerifier, then install the output.
"""
import argparse
import copy
import hashlib
import json
import shutil
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    for ancestor in Path(__file__).resolve().parents:
        libraries = ancestor / '.tools/python'
        if (libraries / 'yaml').is_dir():
            sys.path.insert(0, str(libraries))
            break
    import yaml

PROJECT = Path(__file__).resolve().parents[1]
MAPPING = json.loads((PROJECT / 'tools/upgrade/language-map-1.8.10.json').read_text(encoding='utf-8'))
STOCK_MENUS = {
    'setup': 'e8b5d4f0e14b3e70edd1d0fd1e1d1f1359f9e60d4a38150f4988079415656122',
    'room': 'b427352f17f7a177b4a4a522abb5ea13ea5f52906a52a8621f1baf953aa0c2db',
    'hand': '38f413de466aadea58826919d161b58827c5eeeebd54bed0b8d19699df1264b2',
}
STOCK_CAPTIONS = {'bots': '&f补齐陪练并开始', 'play': '&f回到对局', 'options': '&f房间选项'}


def flatten(values, prefix=''):
    result = {}
    for key, value in values.items():
        path = f'{prefix}.{key}' if prefix else str(key)
        if isinstance(value, dict):
            result.update(flatten(value, path))
        else:
            result[path] = value
    return result


def dump(values):
    return yaml.safe_dump(values, allow_unicode=True, sort_keys=False, width=1000)


def convert_rooms(text):
    data = json.loads(text)
    if data.get('schema', 1) != 1:
        raise ValueError('Unsupported room file schema')
    for room in data['rooms']:
        if room['kind'] in ('lastcard', 'color-eight'):
            if room.get('rulesVersion', 1) != 2:
                raise ValueError('Unsupported card rules version: finish the old game using its original build')
            room['kind'] = 'color-eight'
            options = room.get('options', {})
            if 'finish' in options:
                if options['finish'] not in ('first', 'all'):
                    raise ValueError('Unknown historical finish option')
                del options['finish']
        elif room.get('rulesVersion', 1) != 1:
            raise ValueError('Unsupported room rules version')
        reason = room.get('result', '')
        room['result'] = {'draw:Wall exhausted': 'draw:wall-exhausted',
                          'draw:Shared highest score': 'draw:shared-highest-score'}.get(reason, reason)
    return json.dumps(data, ensure_ascii=False, indent=2) + '\n'


def convert(source, output):
    source, output = source.resolve(), output.resolve()
    if not source.is_dir() or output.exists() or output.is_relative_to(source) or source.is_relative_to(output):
        raise ValueError('Input must exist; output must be a new, separate directory')
    prepared = {}
    report = {'source': str(source), 'output': str(output), 'converted': [], 'unmapped': []}
    previous = source / 'upgrade-report.json'
    if previous.exists():
        report['unmapped'].extend(json.loads(previous.read_text(encoding='utf-8')).get('unmapped', []))
    rooms = source / 'rooms.json'
    if rooms.exists():
        prepared['rooms.json'] = convert_rooms(rooms.read_text(encoding='utf-8-sig'))
    config = source / 'config.yml'
    if config.exists():
        text = config.read_text(encoding='utf-8-sig')
        values = yaml.safe_load(text) or {}
        if values.get('language') == 'en':
            # Only replace the selected locale, retaining all comments and unrelated settings.
            import re
            text = re.sub(r'(?m)^(language\s*:\s*)[\"\']?en[\"\']?(\s*(?:#.*)?)$', r'\1en_US\2', text)
            prepared['config.yml'] = text
    languages = {}
    for directory in ('lang', 'languages'):
        for file in sorted((source / directory).glob('*.yml')):
            locale = 'en_US' if file.stem == 'en' else file.stem
            base = 'zh_CN' if locale == 'zh_CN' else 'en_US'
            defaults = yaml.safe_load((PROJECT / f'src/main/resources/languages/{base}.yml').read_text(encoding='utf-8'))
            target = languages.setdefault(locale, copy.deepcopy(defaults))
            old_defaults = MAPPING['defaults'][base]
            values = yaml.safe_load(file.read_text(encoding='utf-8-sig')) or {}
            fragments = values.pop('translations', {})
            if 'messages' in values:
                wrapped = values.pop('messages')
                values.update(wrapped)
            for key, value in flatten(values).items():
                if not isinstance(value, str):
                    raise ValueError(f'{file.name}: {key} must be a string')
                current = key if key in defaults else MAPPING['keys'].get(key)
                if current in defaults:
                    if key in old_defaults and value == old_defaults[key]:
                        continue
                    target[current] = value
                elif value != old_defaults.get(key):
                    report['unmapped'].append({'file': str(file.relative_to(source)), 'key': key, 'value': value})
            for phrase, value in fragments.items():
                current = MAPPING['sources'].get(phrase)
                if current in defaults and isinstance(value, str):
                    target[current] = value
                else:
                    report['unmapped'].append({'file': str(file.relative_to(source)), 'key': phrase, 'value': value})
    for locale, values in languages.items():
        prepared[f'languages/{locale}.yml'] = dump(dict(sorted(values.items())))
    for file in sorted((source / 'menus').glob('*.yml')):
        text = file.read_text(encoding='utf-8-sig')
        digest = hashlib.sha256(text.replace('\r\n', '\n').strip().encode()).hexdigest()
        if digest == STOCK_MENUS.get(file.stem):
            prepared[f'menus/{file.name}'] = (PROJECT / f'src/main/resources/menus/{file.name}').read_text(encoding='utf-8')
            continue
        values = yaml.safe_load(text) or {}
        changed = False
        for key, button in values.get('Bottom', {}).get('buttons', {}).items():
            if isinstance(button, dict) and button.get('text') == STOCK_CAPTIONS.get(key):
                button['text'] = '<white>@label@'
                changed = True
        if changed:
            prepared[f'menus/{file.name}'] = dump(values)
    # Parsing and rule-version validation finish before any output is created.
    shutil.copytree(source, output)
    for name, text in prepared.items():
        file = output / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text, encoding='utf-8')
        report['converted'].append(name)
    (output / 'upgrade-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    arguments = parser.parse_args()
    result = convert(arguments.source, arguments.output)
    print(f"Prepared {len(result['converted'])} files; {len(result['unmapped'])} custom fragments retained in upgrade-report.json")
