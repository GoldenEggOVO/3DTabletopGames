import unittest
from pathlib import Path
from PIL import Image, ImageChops
from face_art import playing_face, playing_back, joker_face

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'resource-pack/source/playing-cards'


class PlayingCardArtTest(unittest.TestCase):
    def test_all_standard_faces_preserve_the_imported_pixels_and_aspect_ratio(self):
        for kind in ('spades', 'hearts', 'diamonds', 'clubs'):
            for rank in ('ace', *map(str, range(2, 11)), 'jack', 'queen', 'king'):
                with self.subTest(kind=kind, rank=rank):
                    original = Image.open(SOURCE / f'{kind}_{rank}.png').convert('RGB')
                    face = playing_face(ROOT, kind, rank)
                    self.assertEqual((120, 180), face.size)
                    self.assertIsNone(ImageChops.difference(
                        original.resize((120, 168), Image.Resampling.NEAREST),
                        face.crop((0, 6, 120, 174))).getbbox())

    def test_back_and_small_joker_preserve_the_original_artwork(self):
        for file, face in (('back_blue_basic', playing_back(ROOT)), ('joker', joker_face(ROOT, 'small'))):
            original = Image.open(SOURCE / f'{file}.png').convert('RGB')
            self.assertEqual((120, 180), face.size)
            self.assertIsNone(ImageChops.difference(
                original.resize((120, 168), Image.Resampling.NEAREST),
                face.crop((0, 6, 120, 174))).getbbox())

    def test_jokers_remain_distinguishable_without_changing_the_portrait(self):
        small, big = joker_face(ROOT, 'small'), joker_face(ROOT, 'big')
        self.assertIsNotNone(ImageChops.difference(small, big).getbbox())
        self.assertIsNone(ImageChops.difference(
            small.crop((24, 6, 96, 174)), big.crop((24, 6, 96, 174))).getbbox())


if __name__ == '__main__':
    unittest.main()
