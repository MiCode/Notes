"""Source-level regression checks for the two member-C synchronization fixes.

These checks intentionally avoid the Android toolchain so they remain fast,
deterministic regression gates alongside the full Gradle build and lint tasks.
"""

from pathlib import Path
import re
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


def read_source(relative_path):
    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


class GTaskSourceRegressionTests(unittest.TestCase):
    def test_service_checks_restart_intent_before_reading_extras(self):
        source = read_source(
            "src/net/micode/notes/gtask/remote/GTaskSyncService.java"
        )
        method_start = source.index("public int onStartCommand")
        method_end = source.index("public void onLowMemory", method_start)
        method = source[method_start:method_end]

        null_guard = method.index("if (intent == null)")
        extras_read = method.index("intent.getExtras()")

        self.assertLess(null_guard, extras_read)
        self.assertIn("stopSelf(startId);", method)
        self.assertIn("return START_NOT_STICKY;", method)
        self.assertNotIn("return START_STICKY;", method)

    def test_invalidated_token_is_replaced_by_refreshed_token(self):
        source = read_source("src/net/micode/notes/gtask/remote/GTaskClient.java")
        method_start = source.index("private String loginGoogleAccount")
        method_end = source.index("private boolean tryToLoginGtask", method_start)
        method = source[method_start:method_end]

        refresh_branch = re.search(
            r"if\s*\(invalidateToken\)\s*\{(?P<body>.*?)\n\s*\}",
            method,
            re.DOTALL,
        )

        self.assertIsNotNone(refresh_branch)
        self.assertIn(
            "return loginGoogleAccount(activity, false);",
            refresh_branch.group("body"),
        )

    def test_sync_notification_uses_supported_builder_and_channel(self):
        source = read_source(
            "src/net/micode/notes/gtask/remote/GTaskASyncTask.java"
        )

        self.assertNotIn("setLatestEventInfo", source)
        self.assertIn("NotificationChannel", source)
        self.assertIn("new Notification.Builder", source)
        self.assertIn("PendingIntent.FLAG_IMMUTABLE", source)

    def test_sync_progress_broadcast_is_limited_to_this_application(self):
        source = read_source(
            "src/net/micode/notes/gtask/remote/GTaskSyncService.java"
        )
        method_start = source.index("public void sendBroadcast(String msg)")
        method_end = source.index("public static void startSync", method_start)
        method = source[method_start:method_end]

        self.assertIn("intent.setPackage(getPackageName());", method)


if __name__ == "__main__":
    unittest.main()
