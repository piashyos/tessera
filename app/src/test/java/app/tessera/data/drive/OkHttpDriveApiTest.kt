package app.tessera.data.drive

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpDriveApiTest {
    private val server = MockWebServer()
    private lateinit var api: OkHttpDriveApi

    @Before
    fun setUp() {
        server.start()
        api = OkHttpDriveApi(OkHttpClient(), baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `uploadFile posts multipart and parses id`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"id":"f123","resourceKey":"rk1"}"""))

            val file = api.uploadFile(token = "tok", name = "img.webp", parentId = "folder1", appImageId = "uuid-1", bytes = byteArrayOf(1, 2, 3))

            assertEquals("f123", file.id)
            assertEquals("rk1", file.resourceKey)
            val recorded = server.takeRequest()
            assertEquals("Bearer tok", recorded.getHeader("Authorization"))
            assertTrue(recorded.path!!.startsWith("/upload/drive/v3/files?uploadType=multipart"))
            val body = recorded.body.readUtf8()
            assertTrue(body.contains(""""tesseraImageId":"uuid-1""""))
            assertTrue(body.contains(""""parents":["folder1"]"""))
        }

    @Test
    fun `quota 403 maps to DriveQuotaException`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(403)
                    .setBody("""{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}"""),
            )

            assertThrows(DriveQuotaException::class.java) {
                kotlinx.coroutines.runBlocking {
                    api.uploadFile("tok", "img.webp", "folder1", "uuid-1", byteArrayOf(1))
                }
            }
        }

    @Test
    fun `createPermission posts type=user reader with sendNotificationEmail=false`() =
        runTest {
            server.enqueue(MockResponse().setBody("{}"))

            api.createPermission(token = "tok", fileId = "f123", email = "bob@example.com")

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertTrue(recorded.path!!.startsWith("/drive/v3/files/f123/permissions?sendNotificationEmail=false"))
            val body = recorded.body.readUtf8()
            assertTrue(body.contains(""""type":"user""""))
            assertTrue(body.contains(""""role":"reader""""))
            assertTrue(body.contains(""""emailAddress":"bob@example.com""""))
        }

    @Test
    fun `createAnyonePermission posts type=anyone reader`() =
        runTest {
            server.enqueue(MockResponse().setBody("{}"))

            api.createAnyonePermission(token = "tok", fileId = "f123")

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertTrue(recorded.path!!.startsWith("/drive/v3/files/f123/permissions"))
            val body = recorded.body.readUtf8()
            assertTrue(body.contains(""""type":"anyone""""))
            assertTrue(body.contains(""""role":"reader""""))
        }

    @Test
    fun `listPermissions parses id and emailAddress`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"permissions":[{"id":"p1","emailAddress":"bob@example.com","type":"user","role":"reader"},{"id":"p2","type":"anyone","role":"reader"}]}""",
                ),
            )

            val permissions = api.listPermissions(token = "tok", fileId = "f123")

            assertEquals(2, permissions.size)
            assertEquals("p1", permissions[0].id)
            assertEquals("bob@example.com", permissions[0].emailAddress)
            assertEquals("p2", permissions[1].id)
            assertNull(permissions[1].emailAddress)
            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertTrue(recorded.path!!.startsWith("/drive/v3/files/f123/permissions"))
        }

    @Test
    fun `deletePermission hits DELETE path`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))

            api.deletePermission(token = "tok", fileId = "f123", permissionId = "p1")

            val recorded = server.takeRequest()
            assertEquals("DELETE", recorded.method)
            assertEquals("/drive/v3/files/f123/permissions/p1", recorded.path)
        }

    @Test
    fun `trashFile PATCHes trashed=true`() =
        runTest {
            server.enqueue(MockResponse().setBody("{}"))

            api.trashFile(token = "tok", fileId = "f123")

            val recorded = server.takeRequest()
            assertEquals("PATCH", recorded.method)
            assertEquals("/drive/v3/files/f123", recorded.path)
            assertTrue(recorded.body.readUtf8().contains(""""trashed":true"""))
        }

    @Test
    fun `downloadFile returns bytes and sends resource-key header`() =
        runTest {
            server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(1, 2, 3, 4))))

            val bytes = api.downloadFile(token = "tok", fileId = "f123", resourceKey = "rk1")

            assertTrue(bytes.contentEquals(byteArrayOf(1, 2, 3, 4)))
            val recorded = server.takeRequest()
            assertEquals("f123/rk1", recorded.getHeader("X-Goog-Drive-Resource-Keys"))
            assertTrue(recorded.path!!.startsWith("/drive/v3/files/f123?alt=media"))
        }

    @Test
    fun `downloadFile omits header when resourceKey null`() =
        runTest {
            server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(9))))

            api.downloadFile(token = "tok", fileId = "f123", resourceKey = null)

            val recorded = server.takeRequest()
            assertNull(recorded.getHeader("X-Goog-Drive-Resource-Keys"))
        }

    @Test
    fun `401 maps to DriveAuthException`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"errors":[{"reason":"authError"}]}}"""))

            assertThrows(DriveAuthException::class.java) {
                kotlinx.coroutines.runBlocking {
                    api.trashFile("tok", "f123")
                }
            }
        }

    @Test
    fun `500 maps to DriveApiException with code`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"errors":[{"reason":"backendError"}]}}"""))

            val ex =
                assertThrows(DriveApiException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        api.trashFile("tok", "f123")
                    }
                }
            assertEquals(500, ex.code)
        }
}
