package app.tessera.core.config

/**
 * Static configuration for the Drive-backed images feature.
 *
 * [ANYONE_LINK_MODE] selects the permission-sharing scheme. The main scheme (`false`) grants a
 * per-recipient `type=user` reader permission to every shared-note member. The capability-URL
 * fallback (`true`) instead publishes a single `type=anyone` reader link; it is flipped on only if
 * the live spike shows recipients cannot read per-email grants on files created with the
 * `drive.file` scope. See `docs/superpowers/specs/2026-08-27-drive-images-spike-findings.md`.
 */
object DriveImagesConfig {
    const val ANYONE_LINK_MODE = false
}
