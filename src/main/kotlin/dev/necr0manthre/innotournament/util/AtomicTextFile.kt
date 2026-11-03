package dev.necr0manthre.innotournament.util

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFileAttributeView

/** A small, same-directory atomic text replacement; never falls back to non-atomic overwrite. */
class AtomicTextFile {
    fun replace(path: Path, text: String) {
        require(path.toString().isNotEmpty()) { "Settings file path must not be empty" }
        val target = path.toAbsolutePath().normalize()
        val directory = target.parent
        var temporary: Path? = null
        try {
            Files.createDirectories(directory)
            // On POSIX filesystems, atomic replacement otherwise resets an existing file's mode.
            val existingPermissions = if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.getFileAttributeView(target, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
                    ?.readAttributes()?.permissions()
            } else null
            val file = Files.createTempFile(directory, ".${target.fileName}.", ".tmp")
            temporary = file
            Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { writer -> writer.write(text) }
            if (existingPermissions != null) Files.setPosixFilePermissions(file, existingPermissions)
            Files.move(file, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw IOException("Cannot atomically write tournament settings '$target': ${e.message}", e)
        } finally {
            // Cleanup is best-effort; do not mask the primary failed atomic move/write.
            temporary?.let { file ->
                try {
                    Files.deleteIfExists(file)
                } catch (_: IOException) {
                    // A stale .tmp file is never read as a tournament definition.
                }
            }
        }
    }
}
