package com.suseoaa.locationspoofer.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class SpooferProvider : ContentProvider() {

    companion object {
        var isActive = false
        var latitude = 0.0
        var longitude = 0.0
        var wifiJson = "[]"
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf("active", "lat", "lng", "wifi_json"))
        cursor.addRow(arrayOf(if (isActive) 1 else 0, latitude, longitude, wifiJson))
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
