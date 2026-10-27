#!/usr/bin/env python3
"""Deliver a verified development APK to an UNPUBLISHED draft in a PRIVATE repo.

Never use actions/upload-artifact on the public AA-Experiments repository.
Only invoked by the manually selected, privately gated signing job.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import Request, urlopen

API = "https://api.github.com"
SOURCE_REPO = "HighwindBR/AA-Experiments"
SOURCE_BRANCH = "refs/heads/feature/beta4-getter-probe-preflight"
APK_NAME = "AAExperiments-1.0.0-beta4-candidate.apk"


def require_private_destination(info: dict, desired: str) -> None:
    """Fail closed if a GitHub variable points at any public or wrong repo."""
    if desired == SOURCE_REPO or not re.fullmatch(r"HighwindBR/[A-Za-z0-9_.-]+", desired):
        raise ValueError("Delivery must target a separate HighwindBR repository.")
    if info.get("full_name") != desired or info.get("private") is not True:
        raise ValueError("Delivery repository must exist and have private visibility.")


def verify_apk_checksum(apk: Path) -> str:
    if not apk.is_file() or not apk.stat().st_size:
        raise ValueError("Verified APK is missing or empty.")
    if apk.name != APK_NAME:
        raise ValueError("Unexpected APK version/name. Refusing delivery.")
    checksum = Path(str(apk) + ".sha256")
    if not checksum.is_file():
        raise ValueError("The signed APK's SHA-256 sidecar is missing.")
    parts = checksum.read_text(encoding="utf-8").strip().split(maxsplit=1)
    if len(parts) != 2 or not re.fullmatch(r"[0-9a-fA-F]{64}", parts[0]):
        raise ValueError("Malformed APK checksum sidecar.")
    if parts[1].strip().lstrip("*") != apk.name:
        raise ValueError("Checksum sidecar names another file.")
    with apk.open("rb") as source:
        computed = hashlib.file_digest(source, "sha256").hexdigest()
    if computed.lower() != parts[0].lower():
        raise ValueError("APK checksum differs from verified signed candidate.")
    return computed


def draft_tag(run_id: str, attempt: str) -> str:
    if not run_id.isdecimal() or not attempt.isdecimal():
        raise ValueError("GitHub Actions run identity is required.")
    return f"aaxp-private-beta4-candidate-run-{run_id}-attempt-{attempt}"


def github(method: str, url: str, token: str, payload=None, content_type=None) -> dict:
    host = urlsplit(url).hostname
    if urlsplit(url).scheme != "https" or host not in ("api.github.com", "uploads.github.com"):
        raise ValueError("Refusing GitHub credential delivery to unexpected host.")
    data = None
    if payload is not None:
        data = payload if isinstance(payload, bytes) else json.dumps(payload).encode("utf-8")
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if data is not None:
        headers["Content-Type"] = content_type or "application/json"
    request = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=120) as response:
            result = response.read()
            return json.loads(result) if result else {}
    except HTTPError as exc:
        # Never include response bodies: proxies occasionally echo credentials.
        raise RuntimeError(f"GitHub {method} request failed (HTTP {exc.code}).") from None
    except URLError:
        raise RuntimeError("Could not connect to GitHub delivery API.") from None


def run() -> None:
    token = os.environ.get("AAXP_PRIVATE_DELIVERY_TOKEN", "")
    destination = os.environ.get("AAXP_PRIVATE_DELIVERY_REPO", "")
    if not token or not destination:
        raise ValueError(
            "Private delivery requires AAXP_PRIVATE_DELIVERY_TOKEN secret and "
            "AAXP_PRIVATE_DELIVERY_REPO repository variable."
        )
    if os.environ.get("GITHUB_REPOSITORY") != SOURCE_REPO or os.environ.get("GITHUB_REF") != SOURCE_BRANCH:
        raise ValueError("Private delivery is restricted to the beta4 feature branch.")
    sha = os.environ.get("GITHUB_SHA", "")
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid checked-out source commit.")
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    attempt = os.environ.get("GITHUB_RUN_ATTEMPT", "")
    tag = draft_tag(run_id, attempt)
    apk = Path.home() / "aaxp-private-candidates" / APK_NAME
    digest = verify_apk_checksum(apk)

    repo_info = github("GET", f"{API}/repos/{destination}", token)
    require_private_destination(repo_info, destination)
    default_branch = repo_info.get("default_branch")
    if not default_branch:
        raise ValueError("Private delivery repository needs an initialized default branch.")

    release = github("POST", f"{API}/repos/{destination}/releases", token, {
        "tag_name": tag,
        "target_commitish": default_branch,
        "name": f"AA Experiments 1.0.0-beta4 · private candidate build {run_id}/{attempt}",
        "body": (
            "UNPUBLISHED PRIVATE TEST DOWNLOAD. Never publish this draft.\n\n"
            f"Source: {SOURCE_REPO}@{sha}\n"
            f"APK SHA-256: {digest}\n"
            "Requires Android Auto/LSPosed device validation. This is not an "
            "official AA Experiments release or beta4 tag."
        ),
        "draft": True,
        "prerelease": False,
        "make_latest": "false",
    })
    release_id = release.get("id")
    if not isinstance(release_id, int):
        raise RuntimeError("No release ID returned by GitHub.")
    try:
        if release.get("draft") is not True:
            raise RuntimeError("Private download was not created as an unpublished draft.")
        base = release.get("upload_url", "").split("{", 1)[0]
        if urlsplit(base).hostname != "uploads.github.com":
            raise RuntimeError("Invalid GitHub asset upload URL.")
        files = (
            (apk, "application/vnd.android.package-archive"),
            (Path(str(apk) + ".sha256"), "text/plain"),
        )
        for file, mime in files:
            url = f"{base}?name={quote(file.name)}"
            asset = github("POST", url, token, file.read_bytes(), mime)
            if asset.get("name") != file.name or asset.get("size") != file.stat().st_size:
                raise RuntimeError("GitHub did not confirm the full private asset upload.")
        # Double-check against accidental draft/publication status changes.
        verified = github("GET", f"{API}/repos/{destination}/releases/{release_id}", token)
        if verified.get("draft") is not True:
            raise RuntimeError("The private candidate unexpectedly became published.")
    except Exception:
        # Best effort: don't leave a partly uploaded or incorrectly configured draft.
        try:
            github("DELETE", f"{API}/repos/{destination}/releases/{release_id}", token)
        except Exception:
            pass
        raise

    print(f"Private APK delivered in unpublished draft: https://github.com/{destination}/releases")
    print(f"Draft name: {release['name']}")
    print(f"Source commit: {sha}")
    print(f"APK SHA-256: {digest}")
    print("No upload to the public AA-Experiments repo; no official tag or public release.")


if __name__ == "__main__":
    try:
        run()
    except (ValueError, RuntimeError) as exc:
        print(f"Private delivery blocked: {exc}", file=sys.stderr)
        sys.exit(1)
