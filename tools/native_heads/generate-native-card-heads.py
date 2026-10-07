"""Generate Mojang textures with MineSkin V2, preserving progress and keeping the key local."""
import argparse
import base64
import json
import re
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path


def texture(response):
    data = response["skin"]["texture"]["data"]
    decoded = json.loads(base64.b64decode(data["value"], validate=True))
    url = decoded["textures"]["SKIN"]["url"]
    if not re.fullmatch(r"https?://textures\.minecraft\.net/texture/[a-f0-9]{1,64}", url):
        raise ValueError("The generated texture is not a Mojang skin URL")
    if not data.get("signature"):
        raise ValueError("The generated texture has no signature")
    return {"value": data["value"], "signature": data["signature"]}


def delay(response, now):
    rate = response.get("rateLimit", {})
    seconds = rate.get("next", {}).get("relative", 0) / 1000
    for limit in rate.get("limit", {}).values():
        if isinstance(limit, dict) and limit.get("limit") != 0 and limit.get("remaining") == 0:
            seconds = max(seconds, limit.get("reset", now) - now + 1)
    return max(0, seconds)


def catalog(manifest, cache):
    return {face: [cache["textures"][Path(tile["skin"]).stem] for tile in tiles]
            for face, tiles in manifest["faces"].items()}


def save(path, data):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def request(key, method, endpoint, payload=None, content_type=None):
    headers = {"Authorization": "Bearer " + key,
               "User-Agent": "3DTabletopGames/NativeCardTextures"}
    if content_type:
        headers["Content-Type"] = content_type
    req = urllib.request.Request("https://api.mineskin.org/v2/" + endpoint,
                                 data=payload, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=45) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        response = json.load(error)
        if error.code == 429:
            response["retry_after"] = float(error.headers.get("Retry-After", "5"))
            return response
        codes = ",".join(item.get("code", "unknown") for item in response.get("errors", []))
        raise RuntimeError(f"MineSkin HTTP {error.code}: {codes}") from None


def upload(key, image):
    boundary = "tabletop-" + uuid.uuid4().hex
    payload = bytearray()
    for name, value in (("variant", "classic"), ("visibility", "unlisted")):
        payload.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    payload.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="card.png"\r\nContent-Type: image/png\r\n\r\n'.encode())
    payload.extend(image.read_bytes())
    payload.extend(f'\r\n--{boundary}--\r\n'.encode())
    return request(key, "POST", "queue", bytes(payload), "multipart/form-data; boundary=" + boundary)


def wait_until(timestamp):
    while timestamp > time.time():
        seconds = timestamp - time.time()
        if seconds > 10:
            print(f"Waiting for the API limit: {int(seconds)} seconds", flush=True)
        time.sleep(min(45, seconds))


def generate(root, key, cache_path):
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    cache = json.loads(cache_path.read_text(encoding="utf-8")) if cache_path.exists() else {"textures": {}, "jobs": {}}
    skins = sorted({tile["skin"] for tiles in manifest["faces"].values() for tile in tiles})
    completed = sum(Path(path).stem in cache["textures"] for path in skins)
    print(f"Resuming {completed}/{len(skins)} textures", flush=True)
    for path in skins:
        digest = Path(path).stem
        if digest in cache["textures"]:
            continue
        retries = 0
        while digest not in cache["textures"]:
            if digest not in cache["jobs"]:
                wait_until(cache.get("next_request_at", 0))
                response = upload(key, root / path)
                cache["rate_limit"] = response.get("rateLimit", {})
                cache["next_request_at"] = time.time() + max(delay(response, time.time()), response.get("retry_after", 0))
                if "skin" in response:
                    cache["textures"][digest] = texture(response)
                elif "retry_after" not in response:
                    cache["jobs"][digest] = response["job"]["id"]
                save(cache_path, cache)
                continue
            time.sleep(1.1)
            response = request(key, "GET", "queue/" + cache["jobs"][digest])
            if "retry_after" in response:
                wait_until(time.time() + response["retry_after"])
            elif "skin" in response:
                cache["textures"][digest] = texture(response)
                del cache["jobs"][digest]
                save(cache_path, cache)
            elif response.get("job", {}).get("status") == "failed":
                codes = {error.get("code") for error in response.get("errors", [])}
                if not codes.intersection({"proxy_rate_limited", "skin_change_failed"}) or retries >= 3:
                    raise RuntimeError("MineSkin generation failed; progress is saved: " + ",".join(sorted(codes)))
                retries += 1
                del cache["jobs"][digest]
                cache["next_request_at"] = max(cache.get("next_request_at", 0), time.time() + 45)
                save(cache_path, cache)
                print("MineSkin transient generation failure; retrying this skin after the cooldown", flush=True)
        completed += 1
        print(f"Generated {completed}/{len(skins)} textures", flush=True)
    return catalog(manifest, cache)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skins", type=Path, required=True)
    parser.add_argument("--api-key-file", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    key = args.api_key_file.read_text(encoding="utf-8-sig").strip()
    if not key or "\n" in key:
        raise ValueError("Invalid API key file format")
    result = generate(args.skins, key, args.skins / "generation-cache.json")
    save(args.output, result)
    print(f"Saved the complete card-head catalog: {len(result)} faces", flush=True)


if __name__ == "__main__":
    main()
