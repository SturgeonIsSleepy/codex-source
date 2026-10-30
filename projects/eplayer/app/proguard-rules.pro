# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep rules here:

# Coil 3
-keep class coil3.** { *; }

# Retrofit & OkHttp
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Gson
-keep class com.google.gson.** { *; }
-keep class cp.player.model.** { *; }
-keep class cp.player.provider.** { *; }

# Media3 (ExoPlayer)
-keep class androidx.media3.** { *; }

# JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Compose FontVariation inner classes (used via reflection for custom ROND axis)
-keep class androidx.compose.ui.text.font.FontVariation$** { *; }

# SuperLyricApi

-dontwarn android.os.ServiceManager

# Persisted ePlayer library and queue schema.
-keep class cp.player.app.Track { *; }

# Device checks exercise the exact renderer/PCM and module core in release APKs.
-keep class cp.player.ui.artwork.** { *; }
-keep class cp.player.engine.EnergyAudioProcessor { *; }
-keep class cp.player.engine.MusicPulse { *; }
-keep class cp.player.engine.RustEngine { *; }
-keep class cp.player.manager.WitchPlaylist { *; }

-keep class cp.player.app.QrLogin { *; }

# Keep the codec available to the release QR round-trip device check.
-keep class com.google.zxing.** { *; }

# AndroidJUnitRunner shares this runtime dependency with the shrunk application.
-keep class androidx.tracing.** { *; }

# Public data boundary used by release integration checks.
-keep class cp.player.app.MusicRepository { *; }
-keep class cp.player.app.LibraryStore { *; }
-keep class cp.player.app.MusicEntry { *; }
-keep class cp.player.app.Account { *; }
-keep class cp.player.app.MusicDetail { *; }
-keep class cp.player.app.MusicSearch { *; }

-keep class cp.player.app.DownloadRecord { *; }

-keep class cp.player.core.lyrics.LyricsText { *; }
-keep class cp.player.core.lyrics.LyricParser { *; }
-keep class cp.player.core.lyrics.TimedLine { *; }

# Gson cache schema must survive R8 independently of Compose presentation.
-keep class cp.player.core.lyrics.LyricsRepository$CachedLyrics { *; }
