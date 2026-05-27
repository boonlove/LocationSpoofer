package com.suseoaa.locationspoofer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation

@Entity(tableName = "location_records")
data class LocationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lat: Double,
    val lng: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "wifi_devices")
data class WifiDevice(
    @PrimaryKey val bssid: String,
    val ssid: String,
    val frequency: Int,
    val capabilities: String
)

@Entity(
    tableName = "location_wifi",
    primaryKeys = ["locationId", "bssid"],
    foreignKeys = [
        ForeignKey(entity = LocationRecord::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WifiDevice::class, parentColumns = ["bssid"], childColumns = ["bssid"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("bssid")]
)
data class LocationWifi(
    val locationId: Long,
    val bssid: String,
    val level: Int
)

@Entity(tableName = "bluetooth_devices")
data class BluetoothDevice(
    @PrimaryKey val address: String,
    val name: String,
    val scanRecordHex: String
)

@Entity(
    tableName = "location_bluetooth",
    primaryKeys = ["locationId", "address"],
    foreignKeys = [
        ForeignKey(entity = LocationRecord::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BluetoothDevice::class, parentColumns = ["address"], childColumns = ["address"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("address")]
)
data class LocationBluetooth(
    val locationId: Long,
    val address: String,
    val rssi: Int
)

@Entity(tableName = "cell_devices")
data class CellDevice(
    @PrimaryKey val cellKey: String,
    val type: String,
    val mcc: Int,
    val mnc: Int,
    // LTE
    val tac: Int = 0,
    val ci: Int = 0,
    val pci: Int = 0,
    // GSM/WCDMA
    val lac: Int = 0,
    val cid: Int = 0,
    // WCDMA
    val psc: Int = 0,
    // NR
    val nci: Long = 0,
    // CDMA
    val networkId: Int = 0,
    val systemId: Int = 0,
    val basestationId: Int = 0
)

@Entity(
    tableName = "location_cells",
    primaryKeys = ["locationId", "cellKey"],
    foreignKeys = [
        ForeignKey(entity = LocationRecord::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CellDevice::class, parentColumns = ["cellKey"], childColumns = ["cellKey"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("cellKey")]
)
data class LocationCell(
    val locationId: Long,
    val cellKey: String,
    val dbm: Int,
    val isRegistered: Boolean
)

data class LocationWithWifi(
    @Embedded val locationWifi: LocationWifi,
    @Relation(parentColumn = "bssid", entityColumn = "bssid")
    val device: WifiDevice
)

data class LocationWithBluetooth(
    @Embedded val locationBluetooth: LocationBluetooth,
    @Relation(parentColumn = "address", entityColumn = "address")
    val device: BluetoothDevice
)

data class LocationWithCell(
    @Embedded val locationCell: LocationCell,
    @Relation(parentColumn = "cellKey", entityColumn = "cellKey")
    val device: CellDevice
)

data class CompleteLocation(
    @Embedded val location: LocationRecord,
    @Relation(entity = LocationWifi::class, parentColumn = "id", entityColumn = "locationId")
    val wifis: List<LocationWithWifi>,
    @Relation(entity = LocationBluetooth::class, parentColumn = "id", entityColumn = "locationId")
    val bluetooths: List<LocationWithBluetooth>,
    @Relation(entity = LocationCell::class, parentColumn = "id", entityColumn = "locationId")
    val cells: List<LocationWithCell>
)
