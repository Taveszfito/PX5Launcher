package com.dueboysenberry1226.px5launcher.media

import android.net.Uri

enum class MediaKind { IMAGE, VIDEO }

data class MediaEntry(
    val id: Long,
    val kind: MediaKind,
    val uri: Uri,
    val displayName: String?,
    val dateAddedSec: Long,
    val bucketId: String?,
    val bucketName: String?
)

data class MediaAlbum(
    val bucketId: String,
    val bucketName: String,
    val cover: MediaEntry? // borítókép/borítóvideó (legfrissebb)
)
