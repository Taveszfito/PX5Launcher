package com.dueboysenberry1226.px5launcher.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

class MediaStoreRepository(private val context: Context) {
    private val cr: ContentResolver = context.contentResolver

    fun loadImages(limit: Int = 400): List<MediaEntry> {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val out = ArrayList<MediaEntry>(minOf(limit, 200))
        cr.query(uri, projection, null, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            var count = 0
            while (c.moveToNext() && count < limit) {
                val id = c.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(uri, id)
                out += MediaEntry(
                    id = id,
                    kind = MediaKind.IMAGE,
                    uri = contentUri,
                    displayName = c.getString(nameCol),
                    dateAddedSec = c.getLong(dateCol),
                    bucketId = c.getString(bucketIdCol),
                    bucketName = c.getString(bucketNameCol)
                )
                count++
            }
        }
        return out
    }

    fun loadVideos(limit: Int = 400): List<MediaEntry> {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val out = ArrayList<MediaEntry>(minOf(limit, 200))
        cr.query(uri, projection, null, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

            var count = 0
            while (c.moveToNext() && count < limit) {
                val id = c.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(uri, id)
                out += MediaEntry(
                    id = id,
                    kind = MediaKind.VIDEO,
                    uri = contentUri,
                    displayName = c.getString(nameCol),
                    dateAddedSec = c.getLong(dateCol),
                    bucketId = c.getString(bucketIdCol),
                    bucketName = c.getString(bucketNameCol)
                )
                count++
            }
        }
        return out
    }

    /** Albumok: bucketId alapján csoportosítjuk a képeket+videókat, és a legfrissebbet adjuk covernek */
    fun loadAlbums(limitPerType: Int = 600): List<MediaAlbum> {
        val images = loadImages(limitPerType)
        val videos = loadVideos(limitPerType)
        val all = (images + videos)

        val byBucket = all
            .filter { !it.bucketId.isNullOrBlank() }
            .groupBy { it.bucketId!! }

        return byBucket.entries
            .map { (bucketId, items) ->
                val cover = items.maxByOrNull { it.dateAddedSec }
                MediaAlbum(
                    bucketId = bucketId,
                    bucketName = cover?.bucketName ?: "Album",
                    cover = cover
                )
            }
            .sortedByDescending { it.cover?.dateAddedSec ?: 0L }
    }

    fun loadAlbumContent(bucketId: String, limit: Int = 800): List<MediaEntry> {
        // egyszerű: kérjük le mindkettőt, szűrjük bucketId alapján, majd dátum szerint
        val images = loadImages(limit).filter { it.bucketId == bucketId }
        val videos = loadVideos(limit).filter { it.bucketId == bucketId }
        return (images + videos).sortedByDescending { it.dateAddedSec }
    }

    companion object {
        fun requiredPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO
                )
            } else {
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}
