import hashlib
from pathlib import Path
import tempfile
import unittest

from scripts.private_candidate_delivery import (
    APK_NAME, draft_tag, github, require_private_destination, verify_apk_checksum,
)


class PrivateDeliveryTest(unittest.TestCase):
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
                         "aaxp-private-preflight22-run-40431677743-attempt-2")
        for bad in ("", "1/2", "oops", "-1"):
            with self.assertRaises(ValueError):
                draft_tag(bad, "1")


if __name__ == "__main__":
    unittest.main()
