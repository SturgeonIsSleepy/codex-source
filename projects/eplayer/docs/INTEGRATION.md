# ePlayer integration

CPPlayer remains responsible for source modules, metadata, queues, MediaSession, downloads and playback. ePlayer adds an artwork presentation layer rather than a second playback service.

- `WitchPlayerPanel` forwards CPPlayer UI state and callbacks to `WitchPlayerView`.
- `CharacterArtwork` prepares one registered skin restoration; the common portrait is reused for every expression.
- `EyeBlink` changes only clipped eye apertures. Hair, face boundaries and hands remain on the shared portrait.
- `CharacterMotion` uses damped continuous motion and settles after pause. Bitmap density is disabled so geometry stays consistent across device DPI.
- `AudioRhythm` analyzes local recordings for tempo and energy. The ExoPlayer PCM processor and Rust render meter provide live energy for streamed songs. The PCM processor forwards the original bytes unchanged; native metering reads sparse samples and stores one atomic value.
- `WitchPlaylist` validates imported audio before atomically replacing an app-private recording. It coexists with the complete CPPlayer library.
- `MainActivity` observes new launch intents so notification/widget returns can expand an already-running player.

Exclusive USB ownership has no Android lifecycle manager, settings entry, intent filter, or device registration path. The native `uac2` feature is disabled by default. Standard Android audio routing remains intact.

The application ID is `app.eplayer.music`; the original `cp.player` source package is retained for JNI compatibility. ePlayer has independent branding, generated sky/icon artwork, a shared paper palette and one multilingual handwriting font.

Instrumentation covers file validation, actual MediaSession playback, seek/pause/background return, module ZIP/API mapping, native initialization, PCM byte transparency, right-channel metering, portrait invariance, skin palette consistency, motion settling and density-independent drawing.
