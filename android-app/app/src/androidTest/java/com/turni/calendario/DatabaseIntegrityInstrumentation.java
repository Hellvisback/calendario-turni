package com.turni.calendario;

/** Framework-only read probe, independent of Kotlin and optimized application classes. */
public final class DatabaseIntegrityInstrumentation extends android.app.Instrumentation {
    @Override public void onCreate(android.os.Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        android.os.Bundle result = new android.os.Bundle();
        try {
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
}
