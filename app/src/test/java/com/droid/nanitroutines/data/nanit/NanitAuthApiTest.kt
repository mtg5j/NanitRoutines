package com.droid.nanitroutines.data.nanit

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NanitAuthApiTest {

    private lateinit var server: MockWebServer
    private lateinit var authApi: NanitAuthApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        authApi = NanitAuthApi(
            baseUrl = server.url("/").toString().trimEnd('/')
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login returns MfaRequired on HTTP 482 and sends nanit-api-version header`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(482)
                .setBody("""{"mfa_token":"mfa-tok-999","phone_suffix":"1234"}""")
        )

        val result = authApi.login("parent@example.com", "secret123")
        assertTrue(result is NanitLoginResult.MfaRequired)
        assertEquals("mfa-tok-999", (result as NanitLoginResult.MfaRequired).mfaToken)

        val recorded = server.takeRequest()
        assertEquals("/login", recorded.path)
        assertEquals("1", recorded.getHeader("nanit-api-version"))
    }

    @Test
    fun `verifyMfa returns Authenticated tokens on HTTP 201`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"access_token":"acc-123","refresh_token":"ref-456"}""")
        )

        val result = authApi.verifyMfa(
            email = "parent@example.com",
            password = "secret123",
            mfaToken = "mfa-tok-999",
            mfaCode = "654321",
            channel = "email"
        )
        assertTrue(result is NanitLoginResult.Authenticated)
        val auth = result as NanitLoginResult.Authenticated
        assertEquals("acc-123", auth.accessToken)
        assertEquals("ref-456", auth.refreshToken)
    }

    @Test
    fun `fetchBabiesAndDevices parses camera_uid and speaker uid`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "babies": [
                        {
                          "uid": "baby-1",
                          "name": "Leo",
                          "camera_uid": "N301ABC999",
                          "speaker": {
                            "speaker": {
                              "uid": "S501SPEAKER1"
                            }
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val res = authApi.fetchBabiesAndDevices("acc-123")
        assertTrue(res.isSuccess)
        val babies = res.getOrThrow()
        assertEquals(1, babies.size)
        assertEquals("Leo", babies[0].babyName)
        assertEquals("N301ABC999", babies[0].cameraUid)
        assertEquals("S501SPEAKER1", babies[0].speakerUid)

        val recorded = server.takeRequest()
        assertEquals("/babies", recorded.path)
        assertEquals("acc-123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `refreshAccessToken exchanges refresh_token for new access_token and extractJwtExpirationMillis decodes exp`() = runTest {
        // 1. Test JWT exp extraction
        val header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256"}""".toByteArray())
        val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":1800000000}""".toByteArray())
        val sampleJwt = "$header.$payload.sig"
        val extractedMillis = TokenStore.extractJwtExpirationMillis(sampleJwt)
        assertEquals(1800000000000L, extractedMillis)

        // 2. Test /tokens/refresh endpoint
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"access_token":"fresh-access-999","refresh_token":"fresh-refresh-888"}""")
        )

        val refreshResult = authApi.refreshAccessToken("old-refresh-111")
        assertTrue(refreshResult is NanitLoginResult.Authenticated)
        val auth = refreshResult as NanitLoginResult.Authenticated
        assertEquals("fresh-access-999", auth.accessToken)
        assertEquals("fresh-refresh-888", auth.refreshToken)

        val recorded = server.takeRequest()
        assertEquals("/tokens/refresh", recorded.path)
        assertEquals("1", recorded.getHeader("nanit-api-version"))
    }
}
