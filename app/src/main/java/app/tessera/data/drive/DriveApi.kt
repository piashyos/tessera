package app.tessera.data.drive

/**
 * Thin wrapper over the Google Drive REST v3 endpoints Tessera needs for
 * storing note images in the user's own Drive.
 */
interface DriveApi {
    suspend fun uploadFile(
        token: String,
        name: String,
        parentId: String,
        appImageId: String,
        bytes: ByteArray,
    ): DriveFile

    suspend fun findFolder(token: String, name: String): String?

    suspend fun createFolder(token: String, name: String): String

    suspend fun downloadFile(token: String, fileId: String, resourceKey: String?): ByteArray

    suspend fun listPermissions(token: String, fileId: String): List<DrivePermission>

    suspend fun createPermission(token: String, fileId: String, email: String)

    suspend fun createAnyonePermission(token: String, fileId: String)

    suspend fun deletePermission(token: String, fileId: String, permissionId: String)

    suspend fun trashFile(token: String, fileId: String)
}
