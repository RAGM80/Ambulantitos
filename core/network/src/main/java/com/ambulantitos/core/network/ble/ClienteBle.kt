package com.ambulantitos.core.network.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper

@SuppressLint("MissingPermission") // La pantalla solicita los permisos antes de buscar.
class ClienteBle(context: Context) {
    class VendedorCercano internal constructor(
        internal val dispositivo: BluetoothDevice,
        val id: String,
        val nombre: String,
    )

    private val app = context.applicationContext
    private val principal = Handler(Looper.getMainLooper())
    private var escaner: BluetoothLeScanner? = null
    private var busqueda: ScanCallback? = null
    private var conexion: BluetoothGatt? = null
    private var limiteBusqueda: Runnable? = null
    private var limiteConexion: Runnable? = null

    fun buscar(encontrado: (VendedorCercano) -> Unit, terminado: (String?) -> Unit) {
        detenerBusqueda()
        val adapter = PracticaBle.adaptador(app)
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            terminado("Activa Bluetooth en un teléfono compatible con BLE.")
            return
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (busqueda !== this) return
                val nombre = result.scanRecord?.getServiceData(PracticaBle.servicio)
                    ?.toString(Charsets.UTF_8).orEmpty()
                val id = result.device.address
                encontrado(VendedorCercano(result.device, id, nombre))
            }

            override fun onScanFailed(errorCode: Int) {
                if (busqueda !== this) return
                detenerBusqueda()
                terminado("No se pudo iniciar la búsqueda BLE. Intenta de nuevo.")
            }
        }
        escaner = scanner
        busqueda = callback
        val filtro = ScanFilter.Builder().setServiceUuid(PracticaBle.servicio).build()
        val ajustes = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(listOf(filtro), ajustes, callback)
        } catch (_: SecurityException) {
            detenerBusqueda()
            terminado("Se requieren permisos de dispositivos cercanos.")
            return
        } catch (_: IllegalStateException) {
            detenerBusqueda()
            terminado("Activa Bluetooth y vuelve a buscar.")
            return
        }
        limiteBusqueda = Runnable {
            detenerBusqueda()
            terminado(null)
        }.also { principal.postDelayed(it, 10_000L) }
    }

    @Suppress("DEPRECATION") // Esta sobrecarga de connectGatt también está disponible en Android 13.
    fun enviar(vendedor: VendedorCercano, mensaje: String, terminado: (Boolean) -> Unit) {
        detenerBusqueda()
        cerrarConexion()
        val bytes = mensaje.toByteArray(Charsets.UTF_8)
        if (bytes.size !in 1..PracticaBle.MAX_BYTES_MENSAJE) {
            terminado(false)
            return
        }
        fun concluir(exito: Boolean) {
            cerrarConexion()
            terminado(exito)
        }
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                principal.post {
                    if (conexion !== gatt) return@post
                    if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                        concluir(false)
                    } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                        try {
                            if (!gatt.discoverServices()) concluir(false)
                        } catch (_: SecurityException) {
                            concluir(false)
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                principal.post {
                    if (conexion !== gatt) return@post
                    val caracteristica = gatt.getService(PracticaBle.servicio.uuid)
                        ?.getCharacteristic(PracticaBle.mensaje)
                    if (status != BluetoothGatt.GATT_SUCCESS || caracteristica == null) {
                        concluir(false)
                        return@post
                    }
                    try {
                        val resultado = gatt.writeCharacteristic(
                            caracteristica, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                        )
                        if (resultado != BluetoothStatusCodes.SUCCESS) concluir(false)
                    } catch (_: SecurityException) {
                        concluir(false)
                    }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int,
            ) {
                principal.post {
                    if (conexion === gatt && characteristic.uuid == PracticaBle.mensaje) {
                        concluir(status == BluetoothGatt.GATT_SUCCESS)
                    }
                }
            }
        }
        try {
            conexion = vendedor.dispositivo.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            concluir(false)
            return
        }
        if (conexion == null) {
            concluir(false)
            return
        }
        limiteConexion = Runnable { concluir(false) }.also { principal.postDelayed(it, 12_000L) }
    }

    private fun detenerBusqueda() {
        limiteBusqueda?.let(principal::removeCallbacks)
        limiteBusqueda = null
        val callback = busqueda
        busqueda = null
        try {
            callback?.let { escaner?.stopScan(it) }
        } catch (_: SecurityException) {
            // El permiso puede ser revocado durante la búsqueda.
        } catch (_: IllegalStateException) {
            // Bluetooth puede apagarse durante la búsqueda.
        }
        escaner = null
    }

    private fun cerrarConexion() {
        limiteConexion?.let(principal::removeCallbacks)
        limiteConexion = null
        val gatt = conexion
        conexion = null
        try {
            gatt?.close()
        } catch (_: SecurityException) {
            // La conexión se descarta aunque el permiso haya sido revocado.
        }
    }

    fun detener() {
        detenerBusqueda()
        cerrarConexion()
    }
}
