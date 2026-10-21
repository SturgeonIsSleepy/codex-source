---
name: android-cloud
description: Quickly configure and launch an Android 16 emulator in Linux x86_64 remote cloud, including hosts without KVM. Return without waiting for boot.
---

Use bundled `android-cloud`, or `~/.local/bin/android-cloud` if installed.
1. Run `android-cloud up --accept-licenses`. This accepts Android SDK licenses as part of the authorized installation, reuses cached files, automatically chooses KVM or software emulation, and starts the emulator in the background.
2. Once the command returns `starting`, report configuration complete and end the turn. Do not wait for boot, poll status, take screenshots, or test Android unless the user asks. `starting` does not mean Android is ready.
3. Later use `android-cloud status` or `android-cloud adb ...` when the user needs the device. `ready` means API 36 and completed boot. Use `up --wait` only when waiting is requested.
4. On configuration error read `android-cloud logs` once. Never repeatedly start deployments. Use `android-cloud down` to stop only the selected owned instance. Do not build or install APKs without project authorization.

Cache: `ANDROID_CLOUD_HOME`, default `~/.cache/android-cloud`. Persist this directory in the base image or a volume to avoid downloads in new sessions. No persistence across independent cloud sessions is assumed. Preserve platform proxy/TLS policy. Never claim global installation or successful boot based only on installed files.
