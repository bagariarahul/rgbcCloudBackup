data class BackupDirectory(
    val id: String,
    val uri: String,
    val displayName: String,
    val path: String,
    val fileCount: Int = 0,
    val lastScanned: String = "Never"
)

data class FileToBackup(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isBackedUp: Boolean = false
)

data class ScanResults(
    val newFiles: List<FileToBackup> = emptyList(),
    val totalSize: Long = 0,
    val lastScanTime: String = ""
)
