package cp.player.core.provider

/** Exact JNI class exported by the publisher's current NCM modules. */
class JniProvider {
    external fun nativeCallApi(method: String, paramsJson: String): String
    external fun analyzeAudioFile(path: String): String
}
