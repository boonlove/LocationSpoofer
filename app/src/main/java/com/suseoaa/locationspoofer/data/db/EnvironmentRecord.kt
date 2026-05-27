package com.suseoaa.locationspoofer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "environment_records")
data class EnvironmentRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lat: Double,
    val lng: Double,
    val wifiJson: String,
    val cellJson: String,
    val timestamp: Long = System.currentTimeMillis()
)
