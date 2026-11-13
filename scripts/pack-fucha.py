#!/usr/bin/env python3
"""Writes Darek's own fucha's `.darek` envelope from its readable source, unsigned (phase-F.md section 2).

    python3 scripts/pack-fucha.py

`OwnFuchaTest` fails when the asset and the source differ, so edit the source and run this.
"""

import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "app/src/main/fuchy/darek.json"
ASSET = ROOT / "app/src/main/assets/fuchy/darek.darek"


def main() -> None:
    inner = SOURCE.read_bytes()
    json.loads(inner)
    envelope = {"fucha": base64.b64encode(inner).decode("ascii")}
    ASSET.write_text(json.dumps(envelope) + "\n", encoding="utf-8")
    print(f"{ASSET.relative_to(ROOT)} <- {SOURCE.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
