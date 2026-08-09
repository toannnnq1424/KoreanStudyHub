#!/usr/bin/env python3
"""Restore learner-facing TOPIK 35 static assets from verified source files.

The source PDFs/audio are intentionally not committed. Derived learner assets
are content-addressed with the bytes produced on the current toolchain, while
the runtime manifest retains both the original reviewed digest and the restored
digest when upstream/container or renderer bytes differ.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
OPS = ROOT / "docs" / "operations"
IMAGE_OUT = ROOT / "src/main/resources/static/images/practice/topik35"
AUDIO_OUT = ROOT / "src/main/resources/static/audio/practice/topik35"
MANIFEST = OPS / "practice-topik35-runtime-assets.json"


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def render(pdf: Path, page: int, target: Path) -> Path:
    output = target / f"{pdf.stem}-{page}"
    subprocess.run([
        "pdftoppm", "-f", str(page), "-l", str(page), "-r", "200",
        "-png", "-singlefile", str(pdf), str(output)
    ], check=True)
    return output.with_suffix(".png")


def restore_crop(rendered: Path, crop: dict, expected: str,
                 asset_id: str) -> dict:
    box = (crop["x"], crop["y"], crop["x"] + crop["width"],
           crop["y"] + crop["height"])
    temporary = IMAGE_OUT / f".{asset_id}.png"
    with Image.open(rendered) as image:
        image.crop(box).save(temporary)
    actual = digest(temporary)
    target = IMAGE_OUT / f"{actual}.png"
    temporary.replace(target)
    return {
        "assetId": asset_id,
        "kind": "IMAGE",
        "reviewedSha256": expected,
        "runtimeSha256": actual,
        "sizeBytes": target.stat().st_size,
        "reference": f"/images/practice/topik35/{target.name}"
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--listening-writing-pdf", type=Path, required=True)
    parser.add_argument("--reading-pdf", type=Path, required=True)
    parser.add_argument("--audio-mp3", type=Path, required=True)
    arguments = parser.parse_args()
    IMAGE_OUT.mkdir(parents=True, exist_ok=True)
    AUDIO_OUT.mkdir(parents=True, exist_ok=True)
    render_dir = Path("/tmp/ksh-topik35-runtime-render")
    render_dir.mkdir(parents=True, exist_ok=True)

    listening = json.loads((OPS /
        "practice-topik35-listening-question-payload.json").read_text())
    reading = json.loads((OPS /
        "practice-topik35-reading-question-payload.json").read_text())
    documents = {
        "topik35-listening-writing-pdf": arguments.listening_writing_pdf,
        "topik35-reading-pdf": arguments.reading_pdf
    }
    rendered: dict[tuple[str, int], Path] = {}
    assets: list[dict] = []
    for item in listening["visualAssets"] + reading["visualAssets"]:
        source = item["source"]
        key = (source["artifactId"], source["pdfPage"])
        if key not in rendered:
            rendered[key] = render(
                documents[key[0]], key[1], render_dir)
        assets.append(restore_crop(
            rendered[key], source["cropPixels"], item["sha256"],
            item["assetId"]))

    writing_key = ("topik35-listening-writing-pdf", 17)
    if writing_key not in rendered:
        rendered[writing_key] = render(
            documents[writing_key[0]], writing_key[1], render_dir)
    assets.append(restore_crop(
        rendered[writing_key],
        {"x": 295, "y": 395, "width": 1120, "height": 620},
        "47977060c3255f13f67d3f041bfe2d998dc3e0f13e23830e3527363ae8b4bee1",
        "topik35-writing-q53-chart"))

    audio_sha = digest(arguments.audio_mp3)
    audio_target = AUDIO_OUT / f"{audio_sha}.mp3"
    if arguments.audio_mp3.resolve() != audio_target.resolve():
        shutil.copy2(arguments.audio_mp3, audio_target)
    assets.append({
        "assetId": "topik35-listening-program-mp3",
        "kind": "AUDIO",
        "reviewedSha256":
            "0f8f7504849689b15c5dcb5f0892580c81c5285b37ead05058e47a9645a91ee1",
        "runtimeSha256": audio_sha,
        "sizeBytes": audio_target.stat().st_size,
        "reference": f"/audio/practice/topik35/{audio_target.name}"
    })
    MANIFEST.write_text(json.dumps({
        "schemaVersion": "practice-topik35-runtime-assets-v1",
        "bundleId": "topik35-v1",
        "derivation": "SOURCE_BYTES_VERIFIED_CURRENT_RENDER_AND_TRANSCODE",
        "manualQaClaim": "NO_NEW_MANUAL_QA_CLAIM",
        "assets": assets
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
