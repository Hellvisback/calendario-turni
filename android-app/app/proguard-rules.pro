# Proguard rules

# Preserve framework accessibility bridge entry points with R8 full-mode optimization.
-keep class androidx.compose.ui.platform.AndroidComposeView { *; }
-keep class androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat { *; }
-keep class androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat$* { *; }
