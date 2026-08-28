package app.tessera.data.drive

data class DriveFile(val id: String, val resourceKey: String?)

data class DrivePermission(val id: String, val emailAddress: String?, val type: String, val role: String)

open class DriveApiException(val code: Int, message: String) : Exception(message)

class DriveAuthException : DriveApiException(401, "unauthorized")

class DriveQuotaException : DriveApiException(403, "storageQuotaExceeded")
