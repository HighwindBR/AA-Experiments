import hashlib
from pathlib import Path
import tempfile
import unittest

from scripts.private_candidate_delivery import (
    APK_NAME, BETA5_BRANCH, BETA5_APK_NAME, BETA5_RC1_BRANCH,
    BETA5_RC1_APK_NAME, BETA5_FINAL_BRANCH, BETA5_FINAL_APK_NAME, candidate_for_ref,
    draft_tag, github, require_private_destination, verify_apk_checksum,
)


class PrivateDeliveryTest(unittest.TestCase):
    def test_exact_beta5_ref_and_checksum_guard(self):
        expected = (BETA5_APK_NAME, "aaxp-private-beta5-preflight1", "1.0.0-beta5-preflight1")
        self.assertEqual(candidate_for_ref(BETA5_BRANCH), expected)
        self.assertEqual(draft_tag("2048", "3", BETA5_BRANCH),
                         "aaxp-private-beta5-preflight1-run-2048-attempt-3")
        for invalid in ("", "refs/heads/main", "refs/tags/v1.0.0-beta5",
                        BETA5_BRANCH + "-test", "refs/heads/feature/beta5-media-startup"):
            with self.subTest(invalid=invalid):
                with self.assertRaises(ValueError):
                    candidate_for_ref(invalid)
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / BETA5_APK_NAME
            payload = b"dummy unsigned fixture, checksum only"
            apk.write_bytes(payload)
            digest = hashlib.sha256(payload).hexdigest()
            sidecar = Path(str(apk) + ".sha256")
            sidecar.write_text(f"{digest}  {BETA5_APK_NAME}\n")
            self.assertEqual(verify_apk_checksum(apk, BETA5_APK_NAME), digest)
            with self.assertRaises(ValueError):
                verify_apk_checksum(apk)
            sidecar.write_text(f"{digest}  {APK_NAME}\n")
            with self.assertRaises(ValueError):
                verify_apk_checksum(apk, BETA5_APK_NAME)

    def test_rc1_requires_exact_ref_version_and_private_checksum(self):
        expected = (BETA5_RC1_APK_NAME, "aaxp-private-beta5-rc1", "1.0.0-beta5-rc1")
        self.assertEqual(candidate_for_ref(BETA5_RC1_BRANCH), expected)
        self.assertEqual(draft_tag("2048", "3", BETA5_RC1_BRANCH),
                         "aaxp-private-beta5-rc1-run-2048-attempt-3")
        for invalid in (BETA5_RC1_BRANCH + "-extra", "refs/tags/v1.0.0-beta5-rc1"):
            with self.subTest(invalid=invalid):
                with self.assertRaises(ValueError):
                    candidate_for_ref(invalid)
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / BETA5_RC1_APK_NAME
            payload = b"offline checksum fixture, not a release-signed APK"
            apk.write_bytes(payload)
            digest = hashlib.sha256(payload).hexdigest()
            sidecar = Path(str(apk) + ".sha256")
            sidecar.write_text(f"{digest}  {BETA5_RC1_APK_NAME}\n")
            self.assertEqual(verify_apk_checksum(apk, BETA5_RC1_APK_NAME), digest)
            with self.assertRaises(ValueError):
                verify_apk_checksum(apk, BETA5_APK_NAME)
            sidecar.write_text(f"{digest}  {BETA5_APK_NAME}\n")
            with self.assertRaises(ValueError):
                verify_apk_checksum(apk, BETA5_RC1_APK_NAME)

    def test_beta5_final_private_candidate_has_exact_branch_version_and_name(self):
        expected = (BETA5_FINAL_APK_NAME, "aaxp-private-beta5-final", "1.0.0-beta5")
        self.assertEqual(candidate_for_ref(BETA5_FINAL_BRANCH), expected)
        self.assertEqual(draft_tag("2048", "3", BETA5_FINAL_BRANCH),
                         "aaxp-private-beta5-final-run-2048-attempt-3")
        for invalid in (BETA5_FINAL_BRANCH + "-unverified", "refs/tags/v1.0.0-beta5",
                        "refs/heads/main"):
            with self.subTest(invalid=invalid):
                with self.assertRaises(ValueError):
                    candidate_for_ref(invalid)
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / BETA5_FINAL_APK_NAME
            payload = b"test-only private beta5 fixture"
            apk.write_bytes(payload)
            digest = hashlib.sha256(payload).hexdigest()
            sidecar = Path(str(apk) + ".sha256")
            sidecar.write_text(f"{digest}  {BETA5_FINAL_APK_NAME}\n")
            self.assertEqual(verify_apk_checksum(apk, BETA5_FINAL_APK_NAME), digest)
            with self.assertRaises(ValueError):
                verify_apk_checksum(apk, BETA5_RC1_APK_NAME)
            sidecar.write_text(f"{digest}  {BETA5_RC1_APK_NAME}\n")
            with self.assertRaises(ValueError):
                verify_apk_checksum(apk, BETA5_FINAL_APK_NAME)

    def test_destination_must_be_separate_and_private(self):
        require_private_destination(
            {"full_name": "HighwindBR/aaxp-private-candidates", "private": True},
            "HighwindBR/aaxp-private-candidates",
        )
        for info, desired in (
            ({"full_name": "HighwindBR/aaxp-private-candidates", "private": False}, "HighwindBR/aaxp-private-candidates"),
            ({"full_name": "HighwindBR/AA-Experiments", "private": False}, "HighwindBR/AA-Experiments"),
            ({"full_name": "attacker/mirror", "private": True}, "attacker/mirror"),
            ({"full_name": "HighwindBR/another", "private": True}, "HighwindBR/aaxp-private-candidates"),
        ):
            with self.subTest(desired=desired, info=info):
                with self.assertRaises(ValueError):
                    require_private_destination(info, desired)

    def test_checksum_and_exact_apk_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / APK_NAME
            content = b"test signed candidate bytes"
            apk.write_bytes(content)
            checksum = hashlib.sha256(content).hexdigest()
            sidecar = Path(str(apk) + ".sha256")
            sidecar.write_text(f"{checksum}  {APK_NAME}\n")
            self.assertEqual(verify_apk_checksum(apk), checksum)
            apk.write_bytes(content + b"changed")
            with self.assertRaises(ValueError):
                verify_apk_checksum(apk)
            apk.write_bytes(content)
            sidecar.write_text(f"{checksum}  wrong.apk\n")
            with self.assertRaises(ValueError):
                verify_apk_checksum(apk)

    def test_credentials_never_sent_to_unknown_host(self):
        for url in ("http://api.github.com/repos/org/repo",
                    "https://attacker.example/repos/org/repo"):
            with self.subTest(url=url):
                with self.assertRaises(ValueError):
                    github("GET", url, "placeholder")

    def test_unique_run_attempt_draft_name(self):
        self.assertEqual(draft_tag("40431677743", "2"),
                         "aaxp-private-beta4-candidate-run-40431677743-attempt-2")
        for bad in ("", "1/2", "oops", "-1"):
            with self.assertRaises(ValueError):
                draft_tag(bad, "1")


if __name__ == "__main__":
    unittest.main()
