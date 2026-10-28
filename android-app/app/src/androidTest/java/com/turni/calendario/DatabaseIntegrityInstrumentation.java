package com.turni.calendario;

/** Framework-only read probe, independent of Kotlin and optimized application classes. */
public final class DatabaseIntegrityInstrumentation extends android.app.Instrumentation {
    private android.os.Bundle arguments;
    @Override public void onCreate(android.os.Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }
    @Override public void onStart() {
        android.os.Bundle result = new android.os.Bundle();
        try {
            if (arguments != null && arguments.containsKey("navigationCapture")) {
                captureNavigationFrames(result);
                finish(android.app.Activity.RESULT_OK, result);
                return;
            }
            java.io.File file = new java.io.File(getTargetContext().getFilesDir(), "turnisync-database-v3.json");
            StringBuilder input = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096]; int count;
                while ((count = reader.read(buffer)) != -1) input.append(buffer, 0, count);
            }
            org.json.JSONObject json = new org.json.JSONObject(input.toString()); json.remove("exportDate");
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            char[] digits = "0123456789abcdef".toCharArray(); StringBuilder hex = new StringBuilder();
            for (byte value : digest) { int unsigned = value & 255; hex.append(digits[unsigned >>> 4]); hex.append(digits[unsigned & 15]); }
            result.putString("snapshotSha256", hex.toString());
            result.putString("debuggable", Boolean.toString((getTargetContext().getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0));
            finish(android.app.Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", error.toString()); finish(android.app.Activity.RESULT_CANCELED, result);
        }
    }

    /** Inspect navigation on devices without screenrecord, independently of optimized app classes. */
    private void captureNavigationFrames(android.os.Bundle result) throws Exception {
        java.io.File directory = new java.io.File(getTargetContext().getExternalFilesDir(null), "navigation-frames");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cannot create frame directory");
        android.content.Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage("com.turni.calendario");
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivitySync(launch); waitForIdleSync(); android.os.SystemClock.sleep(1800);
        injectTap(618, 2210); android.os.SystemClock.sleep(800);
        android.graphics.Bitmap warmup = getUiAutomation().takeScreenshot();
        if (warmup != null) warmup.recycle();
        android.os.SystemClock.sleep(100);
        for (int destination = 0; destination < 2; destination++) {
            java.util.ArrayList<android.graphics.Bitmap> frames = new java.util.ArrayList<>();
            java.util.ArrayList<Long> times = new java.util.ArrayList<>();
            long start = android.os.SystemClock.uptimeMillis();
            injectTap(destination == 0 ? 462 : 618, 2210);
            while (android.os.SystemClock.uptimeMillis() - start < 1000) {
                android.graphics.Bitmap full = getUiAutomation().takeScreenshot();
                if (full == null) throw new java.io.IOException("Cannot capture navigation frame");
                times.add(android.os.SystemClock.uptimeMillis() - start);
                frames.add(android.graphics.Bitmap.createScaledBitmap(full, 360, 800, true)); full.recycle();
            }
            String prefix = destination == 0 ? "report-" : "calendar-";
            for (int i = 0; i < frames.size(); i++) {
                try (java.io.FileOutputStream stream = new java.io.FileOutputStream(new java.io.File(directory, prefix + times.get(i) + ".png"))) {
                    frames.get(i).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
                } finally { frames.get(i).recycle(); }
            }
            result.putString(prefix + "timesMs", times.toString());
            android.os.SystemClock.sleep(1200);
        }
        result.putString("directory", directory.getAbsolutePath());
    }

    private void injectTap(float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 24, android.view.MotionEvent.ACTION_UP, x, y, 0);
        try { getUiAutomation().injectInputEvent(down, true); getUiAutomation().injectInputEvent(up, true); }
        finally { down.recycle(); up.recycle(); }
    }
}
