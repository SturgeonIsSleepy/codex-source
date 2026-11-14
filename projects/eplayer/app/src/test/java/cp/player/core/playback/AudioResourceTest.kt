package cp.player.core.playback

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class AudioResourceTest {
    private fun resource(fields: String) = AudioResource.parse(JsonParser.parseString("{\"code\":200,\"data\":[{$fields}]}").asJsonObject)
    @Test fun actualBitrateAndFormatNeverShareRequestedQualityCache() {
        val low = resource("\"url\":\"https://cdn.test/a\",\"md5\":\"a\",\"size\":100,\"br\":128000,\"type\":\"mp3\"")
        val high = resource("\"url\":\"https://cdn.test/b\",\"md5\":\"b\",\"size\":200,\"br\":192000,\"type\":\"mp3\"")
        assertNotEquals(low.cacheKey("ncm", "song", "lossless"), high.cacheKey("ncm", "song", "lossless"))
    }
    @Test fun renewedSignedUrlForTheSameBytesReusesCache() {
        val first = resource("\"url\":\"https://cdn.test/a?token=old\",\"md5\":\"same\",\"size\":100")
        val next = resource("\"url\":\"https://other-cdn.test/a?token=new\",\"md5\":\"same\",\"size\":100")
        assertEquals(first.cacheKey("n", "1", "hi"), next.cacheKey("n", "1", "hi"))
    }
    @Test fun unknownRepresentationUsesExactUrlAndHonorsProviderLifetime() {
        val first = resource("\"url\":\"https://cdn.test/a?token=old\",\"expi\":1200")
        val next = resource("\"url\":\"https://cdn.test/a?token=new\"")
        assertNotEquals(first.identity, next.identity)
        assertEquals(1200L, first.lifetimeSeconds)
    }
    @Test fun trialAndFullFileAreIsolatedEvenIfProviderReusesIdentifier() {
        val first = resource("\"url\":\"https://cdn.test/a\",\"md5\":\"same\",\"size\":100")
        val next = resource("\"url\":\"https://cdn.test/a\",\"md5\":\"same\",\"size\":100,\"freeTrialInfo\":{\"start\":40,\"end\":80}")
        assertNotEquals(first.identity, next.identity)
    }
    @Test fun coverArtIsNeverAcceptedAsAnAudioUrl() {
        try {
            AudioResource.parse(JsonParser.parseString("{\"code\":403,\"data\":[{\"url\":null,\"album\":{\"picUrl\":\"https://cdn.test/cover.jpg\"}}]}").asJsonObject)
            fail("Artwork must not become audio")
        } catch (failure: AudioResolutionException) { assertFalse(failure.retryable) }
    }
    @Test fun temporaryNativeUrlFailureCanRetryButRightsFailureCannot() {
        for (code in listOf(403, 500)) {
            try { AudioResource.parse(JsonParser.parseString("{\"code\":$code,\"msg\":\"unavailable\"}").asJsonObject); fail() }
            catch (failure: AudioResolutionException) { assertEquals(code == 500, failure.retryable) }
        }
    }
}
