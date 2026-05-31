package com.suseoaa.locationspoofer.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

    @android.annotation.SuppressLint("MissingPermission", "NewApi")
class EnvironmentScanner(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun scanWifi(): String = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val jsonArray = JSONArray()
        try {
            val results = wifiManager.scanResults
            results.forEach { scanResult ->
                val obj = JSONObject()
                obj.put("bssid", scanResult.BSSID)
                obj.put("ssid", scanResult.SSID)
                obj.put("level", scanResult.level)
                obj.put("capabilities", scanResult.capabilities)
                obj.put("frequency", scanResult.frequency)
                jsonArray.put(obj)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        jsonArray.toString()
    }

    @SuppressLint("MissingPermission")
    suspend fun scanCell(): String = withContext(Dispatchers.IO) {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val jsonArray = JSONArray()
        try {
            val allCellInfo = telephonyManager.allCellInfo
            allCellInfo?.forEach { cellInfo ->
                val obj = JSONObject()
                obj.put("isRegistered", cellInfo.isRegistered)
                
                when (cellInfo) {
                    is CellInfoLte -> {
                        obj.put("type", "LTE")
                        val id = cellInfo.cellIdentity
                        obj.put("mcc", id.mccString?.toIntOrNull() ?: 460)
                        obj.put("mnc", id.mncString?.toIntOrNull() ?: 0)
                        obj.put("tac", id.tac)
                        obj.put("ci", id.ci)
                        obj.put("pci", id.pci)
                        val signal = cellInfo.cellSignalStrength
                        obj.put("dbm", signal.dbm)
                    }
                    is CellInfoGsm -> {
                        obj.put("type", "GSM")
                        val id = cellInfo.cellIdentity
                        obj.put("mcc", id.mccString?.toIntOrNull() ?: 460)
                        obj.put("mnc", id.mncString?.toIntOrNull() ?: 0)
                        obj.put("lac", id.lac)
                        obj.put("cid", id.cid)
                        val signal = cellInfo.cellSignalStrength
                        obj.put("dbm", signal.dbm)
                    }
                    is CellInfoWcdma -> {
                        obj.put("type", "WCDMA")
                        val id = cellInfo.cellIdentity
                        obj.put("mcc", id.mccString?.toIntOrNull() ?: 460)
                        obj.put("mnc", id.mncString?.toIntOrNull() ?: 0)
                        obj.put("lac", id.lac)
                        obj.put("cid", id.cid)
                        obj.put("psc", id.psc)
                        val signal = cellInfo.cellSignalStrength
                        obj.put("dbm", signal.dbm)
                    }
                    is CellInfoNr -> {
                        obj.put("type", "NR")
                        val id = cellInfo.cellIdentity as? android.telephony.CellIdentityNr
                        obj.put("mcc", id?.mccString?.toIntOrNull() ?: 460)
                        obj.put("mnc", id?.mncString?.toIntOrNull() ?: 0)
                        obj.put("tac", id?.tac ?: Int.MAX_VALUE)
                        obj.put("nci", id?.nci ?: Long.MAX_VALUE)
                        obj.put("pci", id?.pci ?: Int.MAX_VALUE)
                        val signal = cellInfo.cellSignalStrength as? android.telephony.CellSignalStrengthNr
                        obj.put("dbm", signal?.dbm ?: 0)
                    }
                    is CellInfoCdma -> {
                        obj.put("type", "CDMA")
                        val id = cellInfo.cellIdentity
                        obj.put("networkId", id.networkId)
                        obj.put("systemId", id.systemId)
                        obj.put("basestationId", id.basestationId)
                        val signal = cellInfo.cellSignalStrength
                        obj.put("dbm", signal.dbm)
                    }
                }
                jsonArray.put(obj)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        jsonArray.toString()
    }

    @SuppressLint("MissingPermission")
    suspend fun scanBluetooth(): String = withContext(Dispatchers.IO) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        val scanner = adapter?.bluetoothLeScanner
        
        val jsonArray = JSONArray()
        if (scanner == null || !adapter.isEnabled) {
            return@withContext jsonArray.toString()
        }

        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val resultsList = mutableListOf<ScanResult>()
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        if (result != null) {
                            resultsList.add(result)
                        }
                    }
                    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                        if (results != null) {
                            resultsList.addAll(results)
                        }
                    }
                    override fun onScanFailed(errorCode: Int) {
                        // ignore
                    }
                }

                // Start scan
                scanner.startScan(callback)

                // Stop scan after 2 seconds
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    delay(2000)
                    try {
                        scanner.stopScan(callback)
                    } catch (e: Exception) {}
                    
                    // Deduplicate and convert to JSON
                    val deduped = resultsList.distinctBy { it.device.address }
                    deduped.forEach { res ->
                        val obj = JSONObject()
                        obj.put("address", res.device.address)
                        obj.put("name", res.device.name ?: "")
                        obj.put("rssi", res.rssi)
                        
                        // Parse scan record bytes if available
                        val recordBytes = res.scanRecord?.bytes
                        if (recordBytes != null) {
                            val hexString = recordBytes.joinToString("") { "%02X".format(it) }
                            obj.put("scanRecordHex", hexString)
                        }
                        jsonArray.put(obj)
                    }
                    if (cont.isActive) {
                        cont.resume(Unit)
                    }
                }
                
                cont.invokeOnCancellation {
                    try { scanner.stopScan(callback) } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        jsonArray.toString()
    }
}
