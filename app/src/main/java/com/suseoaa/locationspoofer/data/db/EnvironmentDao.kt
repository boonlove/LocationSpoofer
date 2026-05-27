package com.suseoaa.locationspoofer.data.db

import androidx.room.*

@Dao
interface EnvironmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(record: LocationRecord): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWifiDevice(device: WifiDevice)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationWifi(record: LocationWifi)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBluetoothDevice(device: BluetoothDevice)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationBluetooth(record: LocationBluetooth)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCellDevice(device: CellDevice)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationCell(record: LocationCell)

    @Transaction
    @Query("""
        SELECT * FROM location_records 
        ORDER BY ((lat - :targetLat)*(lat - :targetLat) + (lng - :targetLng)*(lng - :targetLng)) ASC 
        LIMIT :limit
    """)
    suspend fun getNearestLocations(targetLat: Double, targetLng: Double, limit: Int = 3): List<CompleteLocation>

    @Query("SELECT * FROM location_records")
    suspend fun getAllLocations(): List<LocationRecord>

    @Query("SELECT COUNT(*) FROM location_records")
    suspend fun getRecordCount(): Int

    @Query("DELETE FROM location_records")
    suspend fun clearAll()
}
