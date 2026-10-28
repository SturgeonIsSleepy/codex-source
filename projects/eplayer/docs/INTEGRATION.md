# ePlayer integration

CPPlayer remains responsible for source modules, metadata, queues, MediaSession, downloads and playback. ePlayer adds an artwork presentation layer rather than a second playback service.

- `app/PlayerScreen.kt` projects the service state into the ePlayer layout and the artwork-only `WitchPlayerView`; it owns neither decoder nor a second queue.
- `CharacterArtwork` prepares one registered skin restoration; the common portrait is reused for every expression.
- `EyeBlink` changes only clipped eye apertures. Hair, face boundaries and hands remain on the shared portrait.
- `CharacterMotion` uses damped continuous motion and settles after pause. Bitmap density is disabled so geometry stays consistent across device DPI.
- Production artwork reads live energy from the ExoPlayer PCM processor and Rust render meter. `AudioRhythm` retains the reference offline analysis model; the current artwork path passes no offline rhythm. The PCM processor forwards the original bytes unchanged; native metering reads sparse samples and stores one atomic value.
- `WitchPlaylist` validates imported audio before atomically replacing an app-private recording. It coexists with the complete CPPlayer library.
- `MainActivity` observes new launch intents so notification/widget returns can expand an already-running player.

Exclusive USB ownership has no Android lifecycle manager, settings entry, intent filter, or device registration path. The native `uac2` feature is disabled by default. Standard Android audio routing remains intact.

The application ID is `app.eplayer.music`; the original `cp.player` source package is retained for JNI compatibility. ePlayer has independent branding, generated sky/icon artwork, a shared paper palette and one multilingual handwriting font.

Instrumentation covers file validation, actual MediaSession playback, seek/pause/background return, module ZIP/API mapping, native initialization, PCM byte transparency, right-channel metering, portrait invariance, skin palette consistency, motion settling and density-independent drawing.
