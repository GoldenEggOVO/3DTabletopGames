import unittest
from pathlib import Path
from PIL import Image, ImageChops
from tools.resource_pack.face_art import playing_face, playing_back, joker_face, mahjong_face

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'resource-pack/textures/playing-cards'


class PlayingCardArtTest(unittest.TestCase):
    def test_all_standard_faces_preserve_the_imported_pixels_and_aspect_ratio(self):
        for kind in ('spades', 'hearts', 'diamonds', 'clubs'):
            for rank in ('ace', *map(str, range(2, 11)), 'jack', 'queen', 'king'):
                with self.subTest(kind=kind, rank=rank):
                    original = Image.open(SOURCE / f'{kind}_{rank}.png').convert('RGB')
                    face = playing_face(ROOT, kind, rank)
                    self.assertEqual((120, 168), face.size)
                    self.assertIsNone(ImageChops.difference(
                        original.resize((120, 168), Image.Resampling.NEAREST),
                        face).getbbox())

    def test_back_and_small_joker_preserve_the_original_artwork(self):
        for file, face in (('back_blue_basic', playing_back(ROOT)), ('joker', joker_face(ROOT, 'small'))):
            original = Image.open(SOURCE / f'{file}.png').convert('RGB')
            self.assertEqual((120, 168), face.size)
            self.assertIsNone(ImageChops.difference(
                original.resize((120, 168), Image.Resampling.NEAREST),
                face).getbbox())

    def test_jokers_remain_distinguishable_without_changing_the_portrait(self):
        small, big = joker_face(ROOT, 'small'), joker_face(ROOT, 'big')
        self.assertIsNotNone(ImageChops.difference(small, big).getbbox())
        self.assertIsNone(ImageChops.difference(
            small.crop((24, 0, 96, 168)), big.crop((24, 0, 96, 168))).getbbox())

    def test_mahjong_art_is_larger_centered_and_keeps_a_clear_margin(self):
        for key in ('m1', 'm9', 'p9', 's1', 'z1', 'f1'):
            with self.subTest(key=key):
                face = mahjong_face(ROOT, key)
                bounds = ImageChops.difference(face, Image.new('RGB', face.size, '#fff9eb')).getbbox()
                self.assertIsNotNone(bounds)
                self.assertGreaterEqual(bounds[0], 8)
                self.assertGreaterEqual(bounds[1], 12)
                self.assertLessEqual(bounds[2], 152)
                self.assertLessEqual(bounds[3], 228)
                self.assertLessEqual(abs(bounds[0] + bounds[2] - 160), 2)
                self.assertLessEqual(abs(bounds[1] + bounds[3] - 240), 2)
                original = Image.open(ROOT / f'resource-pack/textures/mahjong/tiles/{key}.png').convert('RGBA')
                crop = original.getbbox()
                old_scale = min(140 / original.width, 220 / original.height)
                self.assertGreater(bounds[2] - bounds[0], (crop[2] - crop[0]) * old_scale * 1.18)

    def test_white_dragon_stays_blank(self):
        face = mahjong_face(ROOT, 'z5')
        self.assertIsNone(ImageChops.difference(face, Image.new('RGB', (160, 240), '#fff9eb')).getbbox())


if __name__ == '__main__':
    unittest.main()
