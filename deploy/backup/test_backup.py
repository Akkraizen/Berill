"""Offline regression tests: real archive/retention logic, stubbed pg_dump/rclone/restic.

Run with Python 3 on Linux, or set BASH_EXE to Git Bash on Windows.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest


def shell_path(path):
    value = str(path).replace("\\", "/")
    return "/" + value[0].lower() + value[2:] if len(value) > 1 and value[1] == ":" else value


class BackupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.attachments = self.root / "attachments"
        self.attachments.mkdir()
        (self.attachments / "legacy-photo").write_bytes(b"encrypted legacy")
        self.backups = self.root / "backups"
        self.backups.mkdir()
        self.old = self.backups / "berill-old.dump"
        self.old.write_bytes(b"old dump")
        os.utime(self.old, (1, 1))
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.log = self.root / "calls"
        self.stub("pg_dump", '\n'.join([
            'for arg do',
            ' case "$arg" in --file=*) printf "database" > "${arg#--file=}";; esac',
            'done',
        ]))
        self.stub("rclone", '''
echo "rclone $*" >> "$TEST_LOG"
[ "${FAIL_RCLONE:-0}" = 0 ] || exit 1
printf "encrypted cloud" > "$3/cloud-photo"
''')
        self.stub("restic", '''
echo "restic $*" >> "$TEST_LOG"
[ "${FAIL_RESTIC:-0}" = 0 ] || exit 1
''')
        script = Path(__file__).with_name("backup.sh").read_text()
        script = script.replace("/backups", shell_path(self.backups)).replace("/attachments", shell_path(self.attachments))
        self.script = self.root / "backup.sh"
        self.script.write_text(script)

    def tearDown(self):
        self.temp.cleanup()

    def stub(self, name, text):
        path = self.bin / name
        path.write_text("#!/bin/sh\nset -eu\n" + text)
        path.chmod(0o755)

    def run_backup(self, **extra):
        env = os.environ.copy()
        # No real credentials or repositories are used by these tests.
        env.update(BACKUP_ONCE="1", RESTIC_REPOSITORY="", ATTACHMENTS_BACKEND="local",
                   TEST_LOG=shell_path(self.log), BACKUP_KEEP_DAYS="1",
                   PATH=shell_path(self.bin) + ":" + env["PATH"])
        env.update(extra)
        return subprocess.run([os.environ.get("BASH_EXE", shutil.which("bash") or "/bin/sh"),
                               shell_path(self.script)], env=env, capture_output=True, text=True)

    def archive(self):
        archives = list(self.backups.glob("*.attachments.tar"))
        self.assertEqual(1, len(archives))
        with tarfile.open(archives[0]) as archive:
            return {item.name.removeprefix("./") for item in archive.getmembers()}

    def test_local_archive_and_retention(self):
        result = self.run_backup()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("legacy-photo", self.archive())
        self.assertFalse(self.old.exists())

    def test_s3_archive_includes_legacy_and_cloud_and_restic_receives_archive(self):
        result = self.run_backup(ATTACHMENTS_BACKEND="s3", ATTACHMENTS_S3_BUCKET="test-bucket",
                                 ATTACHMENTS_AWS_ACCESS_KEY_ID="test", ATTACHMENTS_AWS_SECRET_ACCESS_KEY="test",
                                 RESTIC_REPOSITORY="test-only")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue({"legacy-photo", "cloud-photo"}.issubset(self.archive()))
        calls = self.log.read_text()
        self.assertIn(".attachments.tar", calls)
        self.assertIn("--group-by host,tags", calls)
        self.assertNotIn("restic init", calls)

    def test_remote_copy_failure_preserves_old_backups(self):
        result = self.run_backup(ATTACHMENTS_BACKEND="s3", ATTACHMENTS_S3_BUCKET="test-bucket",
                                 ATTACHMENTS_AWS_ACCESS_KEY_ID="test", ATTACHMENTS_AWS_SECRET_ACCESS_KEY="test",
                                 RESTIC_REPOSITORY="test-only", FAIL_RCLONE="1")
        self.assertNotEqual(0, result.returncode)
        self.assertTrue(self.old.exists())
        self.assertNotIn("restic", self.log.read_text())

    def test_repository_failure_never_initializes_or_prunes(self):
        result = self.run_backup(RESTIC_REPOSITORY="test-only", FAIL_RESTIC="1")
        self.assertNotEqual(0, result.returncode)
        self.assertTrue(self.old.exists())
        calls = self.log.read_text()
        self.assertNotIn("init", calls)
        self.assertNotIn("forget", calls)

    def test_invalid_retention_is_rejected(self):
        self.assertNotEqual(0, self.run_backup(BACKUP_KEEP_DAYS="0").returncode)
        self.assertTrue(self.old.exists())


if __name__ == "__main__":
    unittest.main()
