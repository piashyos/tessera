package app.tessera.data.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.net.URLEncoder

class OkHttpDriveApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://www.googleapis.com/",
) : DriveApi {

    override suspend fun uploadFile(
        token: String,
        name: String,
        parentId: String,
        appImageId: String,
        bytes: ByteArray,
    ): DriveFile =
        withContext(Dispatchers.IO) {
            val metadata =
                JSONObject().apply {
                    put("name", name)
                    put("parents", listOf(parentId))
                    put("appProperties", JSONObject().apply { put("tesseraImageId", appImageId) })
                }
            val body =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(MultipartBody.Part.create(metadata.toString().toRequestBody("application/json".toMediaType())))
                    .addPart(MultipartBody.Part.create(bytes.toRequestBody("image/webp".toMediaType())))
                    .build()
            val request =
                Request.Builder()
                    .url("${baseUrl}upload/drive/v3/files?uploadType=multipart")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
            client.newCall(request).execute().use { response ->
                check(response)
                val json = JSONObject(response.body!!.string())
                DriveFile(id = json.getString("id"), resourceKey = json.optString("resourceKey", null))
            }
        }

    override suspend fun findFolder(token: String, name: String): String? =
        withContext(Dispatchers.IO) {
            val escaped = name.replace("'", "\\'")
            val query = "name='$escaped' and mimeType='application/vnd.google-apps.folder' and trashed=false"
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request =
                Request.Builder()
                    .url("${baseUrl}drive/v3/files?q=$encoded")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            client.newCall(request).execute().use { response ->
                check(response)
                val json = JSONObject(response.body!!.string())
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    files.getJSONObject(0).getString("id")
                } else {
                    null
                }
            }
        }

    override suspend fun createFolder(token: String, name: String): String =
        withContext(Dispatchers.IO) {
            val metadata =
                JSONObject().apply {
                    put("name", name)
                    put("mimeType", "application/vnd.google-apps.folder")
                }
            val request =
                Request.Builder()
                    .url("${baseUrl}drive/v3/files")
                    .header("Authorization", "Bearer $token")
                    .post(metadata.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            client.newCall(request).execute().use { response ->
                check(response)
                JSONObject(response.body!!.string()).getString("id")
            }
        }

    override suspend fun downloadFile(token: String, fileId: String, resourceKey: String?): ByteArray =
        withContext(Dispatchers.IO) {
            val builder =
                Request.Builder()
                    .url("${baseUrl}drive/v3/files/$fileId?alt=media")
                    .header("Authorization", "Bearer $token")
                    .get()
            if (resourceKey != null) {
                builder.header("X-Goog-Drive-Resource-Keys", "$fileId/$resourceKey")
            }
            client.newCall(builder.build()).execute().use { response ->
                check(response)
                response.body!!.bytes()
            }
        }

    override suspend fun listPermissions(token: String, fileId: String): List<DrivePermission> =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url("${baseUrl}drive/v3/files/$fileId/permissions?fields=permissions(id,emailAddress,type,role)")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            client.newCall(request).execute().use { response ->
                check(response)
                val json = JSONObject(response.body!!.string())
                val permissions = json.optJSONArray("permissions") ?: return@withContext emptyList()
                (0 until permissions.length()).map { i ->
                    val p = permissions.getJSONObject(i)
                    DrivePermission(
                        id = p.getString("id"),
                        emailAddress = p.optString("emailAddress", null),
                        type = p.getString("type"),
                        role = p.getString("role"),
                    )
                }
            }
        }

    override suspend fun createPermission(token: String, fileId: String, email: String) =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject().apply {
                    put("type", "user")
                    put("role", "reader")
                    put("emailAddress", email)
                }
            val request =
                Request.Builder()
                    .url("${baseUrl}drive/v3/files/$fileId/permissions?sendNotificationEmail=false")
                    .header("Authorization", "Bearer $token")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            client.newCall(request).execute().use { response -> check(response) }
        }

    override suspend fun createAnyonePermission(token: String, fileId: String) =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject().apply {
                    put("type", "anyone")
                    put("role", "reader")
                }
            val request =
                Request.Builder()
                    .url("${baseUrl}drive/v3/files/$fileId/permissions")
                    .header("Authorization", "Bearer $token")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            client.newCall(request).execute().use { response -> check(response) }
        }

    override suspend fun deletePermission(token: String, fileId: String, permissionId: String) =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url("${baseUrl}drive/v3/files/$fileId/permissions/$permissionId")
                    .header("Authorization", "Bearer $token")
                    .delete()
                    .build()
            client.newCall(request).execute().use { response -> check(response) }
        }

    override suspend fun trashFile(token: String, fileId: String) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply { put("trashed", true) }
            val request =
                Request.Builder()
                    .url("${baseUrl}drive/v3/files/$fileId")
                    .header("Authorization", "Bearer $token")
                    .patch(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            client.newCall(request).execute().use { response -> check(response) }
        }

    private fun check(response: Response) {
        if (response.isSuccessful) return
        val bodyString = response.peekBody(Long.MAX_VALUE).string()
        val reason =
            runCatching {
                JSONObject(bodyString)
                    .getJSONObject("error")
                    .getJSONArray("errors")
                    .getJSONObject(0)
                    .getString("reason")
            }.getOrNull()
        when {
            response.code == 401 -> throw DriveAuthException()
            response.code == 403 && reason == "storageQuotaExceeded" -> throw DriveQuotaException()
            else -> throw DriveApiException(response.code, "Drive API error: ${response.code}")
        }
    }
}
