"""Check safe texture extraction and rate-limit handling with recorded API shapes."""
import base64
import importlib.util
import json
import unittest
import tempfile
from unittest.mock import patch
from pathlib import Path


SPEC = importlib.util.spec_from_file_location("generator", Path(__file__).with_name("generate-native-card-heads.py"))


class GenerateNativeCardHeadsTest(unittest.TestCase):
    def setUp(self):
        self.generator = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(self.generator)

    def response(self, url):
        value = base64.b64encode(json.dumps({"textures": {"SKIN": {"url": url}}}).encode()).decode()
        return {"skin": {"texture": {"data": {"value": value, "signature": "signed-fixture"}}}}

    def test_signed_property_is_preserved_and_only_mojang_skin_urls_are_accepted(self):
        response = self.response("http://textures.minecraft.net/texture/" + "a" * 64)
        self.assertEqual(response["skin"]["texture"]["data"], self.generator.texture(response))
        short_hash = self.response("http://textures.minecraft.net/texture/ec42e49bb64b22303c90ff573647cd999e4dff996b2f216c4ae1ab7b761c9fd")
        self.assertEqual(short_hash["skin"]["texture"]["data"], self.generator.texture(short_hash))
        for url in ("https://example.com/texture/" + "a" * 64,
                    "https://textures.minecraft.net/texture/not-a-hash"):
            with self.assertRaises(ValueError):
                self.generator.texture(self.response(url))

    def test_wait_uses_exhausted_hour_and_minute_limits(self):
        response = {"rateLimit": {"next": {"relative": 2000}, "limit": {
            "minute": {"remaining": 0, "reset": 130},
            "hour": {"remaining": 0, "reset": 300}}}}
        self.assertEqual(201, self.generator.delay(response, 100))
        response["rateLimit"]["limit"]["hour"]["remaining"] = 1
        self.assertEqual(31, self.generator.delay(response, 100))

    def test_successful_cache_is_sufficient_to_build_complete_face_catalog(self):
        manifest = {"faces": {"back": [{"skin": "skins/" + "b" * 64 + ".png", "row": 0, "column": 0}]}}
        item = self.generator.texture(self.response("https://textures.minecraft.net/texture/" + "a" * 64))
        catalog = self.generator.catalog(manifest, {"textures": {"b" * 64: item}})
        self.assertEqual(item, catalog["back"][0])
        with self.assertRaises(KeyError):
            self.generator.catalog(manifest, {"textures": {}})

    def test_proxy_backlog_retries_without_regenerating_completed_textures(self):
        temporary = Path(__file__).resolve().parents[1] / "target/head-tests"
        temporary.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=temporary) as directory:
            root = Path(directory)
            (root / "skins").mkdir()
            digest = "b" * 64
            (root / "skins" / (digest + ".png")).write_bytes(b"skin-fixture")
            (root / "manifest.json").write_text(json.dumps({"faces": {"back": [
                {"skin": "skins/" + digest + ".png"}, {"skin": "skins/" + "c" * 64 + ".png"}]}}))
            saved = self.generator.texture(self.response("https://textures.minecraft.net/texture/" + "a" * 64))
            cache = root / "generation-cache.json"
            cache.write_text(json.dumps({"textures": {"c" * 64: saved}, "jobs": {digest: "previous-job"}}))
            replies = iter([{"job": {"status": "failed"}, "errors": [{"code": "proxy_rate_limited"}]},
                            {"job": {"id": "retry-job"}},
                            {"job": {"status": "failed"}, "errors": [{"code": "skin_change_failed"}]},
                            self.response("https://textures.minecraft.net/texture/" + "d" * 64)])
            with patch.object(self.generator, "request", side_effect=lambda *args: next(replies)), \
                    patch.object(self.generator.time, "sleep"), patch.object(self.generator, "wait_until"):
                result = self.generator.generate(root, "local-test-key", cache)
            self.assertEqual(saved, result["back"][1])
            self.assertEqual({}, json.loads(cache.read_text())["jobs"])
            self.assertTrue(result["back"][0]["value"])


if __name__ == "__main__":
    unittest.main()
