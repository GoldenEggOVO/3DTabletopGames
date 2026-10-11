import unittest
import json
from pathlib import Path
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'resource-pack/originals/textures/playing-cards'


def face(name):
    model = json.loads((ROOT / f'resource-pack/assets/tabletop3d/models/item/{name}.json').read_text())
    texture = model['elements'][0]['faces']['south']['texture'][1:]
    path = model['textures'][texture].replace(':', '/textures/') + '.png'
    return Image.open(ROOT / 'resource-pack/assets' / path).convert('RGB')


class PlayingCardArtTest(unittest.TestCase):
    def test_all_standard_faces_preserve_the_imported_pixels_and_aspect_ratio(self):
        for kind in ('spades', 'hearts', 'diamonds', 'clubs'):
            for rank in ('ace', *map(str, range(2, 11)), 'jack', 'queen', 'king'):
                with self.subTest(kind=kind, rank=rank):
                    original = Image.open(SOURCE / f'{kind}_{rank}.png').convert('RGB')
                    artwork = face(f'playing_{kind}_{rank}')
                    self.assertEqual((256, 256), artwork.size)
                    self.assertIsNone(ImageChops.difference(
                        original.resize((120, 168), Image.Resampling.NEAREST).resize((256, 256), Image.Resampling.NEAREST),
                        artwork).getbbox())

    def test_back_and_small_joker_preserve_the_original_artwork(self):
        for file, name in (('back_blue_basic', 'playing_back'), ('joker', 'playing_joker_small')):
            original = Image.open(SOURCE / f'{file}.png').convert('RGB')
            artwork = face(name)
            self.assertIsNone(ImageChops.difference(
                original.resize((120, 168), Image.Resampling.NEAREST).resize((256, 256), Image.Resampling.NEAREST),
                artwork).getbbox())

    def test_jokers_remain_distinguishable_without_changing_the_portrait(self):
        small, big = face('playing_joker_small'), face('playing_joker_big')
        self.assertIsNotNone(ImageChops.difference(small, big).getbbox())
        self.assertIsNone(ImageChops.difference(
            small.crop((64, 0, 192, 256)), big.crop((64, 0, 192, 256))).getbbox())

    def test_mahjong_art_is_larger_centered_and_keeps_a_clear_margin(self):
        for key in ('m1', 'm9', 'p9', 's1', 'z1', 'f1'):
            with self.subTest(key=key):
                artwork = face('mahjong_' + key)
                bounds = ImageChops.difference(artwork, Image.new('RGB', artwork.size, '#fff9eb')).getbbox()
                self.assertIsNotNone(bounds)
                self.assertGreaterEqual(bounds[0], 8)
                self.assertGreaterEqual(bounds[1], 12)
                self.assertLessEqual(bounds[2], 152)
                self.assertLessEqual(bounds[3], 228)
                self.assertLessEqual(abs(bounds[0] + bounds[2] - 160), 2)
                self.assertLessEqual(abs(bounds[1] + bounds[3] - 240), 2)
                original = Image.open(ROOT / f'resource-pack/originals/textures/mahjong/tiles/{key}.png').convert('RGBA')
                crop = original.getbbox()
                old_scale = min(140 / original.width, 220 / original.height)
                self.assertGreater(bounds[2] - bounds[0], (crop[2] - crop[0]) * old_scale * 1.18)

    def test_white_dragon_stays_blank(self):
        artwork = face('mahjong_z5')
        self.assertIsNone(ImageChops.difference(artwork, Image.new('RGB', (160, 240), '#fff9eb')).getbbox())


if __name__ == '__main__':
    unittest.main()
