# Integrated native app review

Captured from the real API 35 app/emulator, not a browser mock:

- [Final beta main screen](main-beta.png)
- [Main immediately after granting access](main-monitoring.png)
- [Settings](settings.png)
- [Actual background connection-loss notification](outage-notification.png)

The final beta main capture shows healthy service with contrasting system-bar icons. The earlier grant capture shows the normal 30-second connection confirmation immediately after granting access. The notification capture comes from the beta's real 2-minute alert deadline after cellular registration was removed while Wi-Fi remained connected and the app screen was closed. Its elapsed time is Android's native chronometer; the quiet monitoring notification is separate. Emulator system/serial-console indicators are Android-owned and not part of the app.

This is visual evidence for the test APK, not a physical-phone audio, deep-sleep, battery, or full accessibility approval.
