#!/usr/bin/env python3
"""Fetch the pinned Kenney Graveyard Kit skeleton OBJ for packaged mobile builds."""

from hashlib import sha1
from pathlib import Path
from urllib.request import Request, urlopen

SOURCE_URL = (
    "https://raw.githubusercontent.com/Pallarran/Myfirstgame/"
    "a31814aedeb635fef4d9594180df6d1b6bb51d6d/"
    "project/assets/models/kenney_graveyard-kit_5.0/Models/OBJ%20format/"
    "character-skeleton.obj"
)
EXPECTED_GIT_BLOB_SHA = "2e2c2d3825c9481c67dbd4bd3e238ddbd8879b74"
DESTINATION = Path("app/src/main/assets/models/character-skeleton.obj")


def git_blob_sha(data: bytes) -> str:
    header = f"blob {len(data)}\0".encode("ascii")
    return sha1(header + data).hexdigest()


def main() -> None:
    request = Request(SOURCE_URL, headers={"User-Agent": "BalancePoint-CI/1.0"})
    with urlopen(request, timeout=30) as response:
        data = response.read()

    actual = git_blob_sha(data)
    if actual != EXPECTED_GIT_BLOB_SHA:
        raise SystemExit(
            f"Skeleton rider source hash mismatch: expected {EXPECTED_GIT_BLOB_SHA}, got {actual}"
        )

    # The original OBJ references Kenney's palette MTL/texture. Balance Point deliberately
    # supplies its own bone material at runtime, so remove those two directives and keep the
    # original geometry, groups, normals and faces unchanged.
    text = data.decode("utf-8")
    text = "\n".join(
        line for line in text.splitlines()
        if not line.startswith("mtllib ") and not line.startswith("usemtl ")
    ) + "\n"

    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    DESTINATION.write_text(text, encoding="utf-8")
    print(f"Staged Kenney skeleton rider: {DESTINATION} ({len(text)} chars)")


if __name__ == "__main__":
    main()
