package com.ambulantitos.core.network.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Handler
import android.os.Looper

@SuppressLint("MissingPermission") // La pantalla solicita los permisos antes de abrir el servidor.
class VendedorBle(context: Context) {
    private val app = context.applicationContext
    private val principal = Handler(Looper.getMainLooper())
    private var servidor: BluetoothGattServer? = null
    private var publicidad: BluetoothLeAdvertiser? = null
    private var anuncio: AdvertiseCallback? = null
    private var sesion: BluetoothGattServerCallback? = null
    private var espera: Runnable? = null

    fun iniciar(nombre: String, vendorId: String, estado: (String) -> Unit) {
        detener()
        require(PracticaBle.formatoVendorId.matches(vendorId))
        val identidad = vendorId.toByteArray(Charsets.UTF_8)
        val adapter = PracticaBle.adaptador(app)
        val anunciante = adapter?.takeIf { it.isEnabled }?.bluetoothLeAdvertiser
        if (anunciante == null) {
            estado("Activa Bluetooth. El teléfono vendedor debe admitir anuncios BLE.")
            return
        }
        fun fallar() {
            detener()
            estado("No se pudo iniciar el anuncio BLE. Desactiva y vuelve a activar el modo local.")
        }
        val anuncioActual = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                if (anuncio !== this) return
                espera?.let(principal::removeCallbacks)
                espera = null
                estado("Anuncio activo. Los clientes cercanos pueden encontrar tu negocio.")
            }

            override fun onStartFailure(errorCode: Int) {
                if (anuncio === this) fallar()
            }
        }
        val callback = object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                principal.post {
                    if (sesion !== this) return@post
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        fallar()
                        return@post
                    }
                    val settings = AdvertiseSettings.Builder().setConnectable(true).build()
                    val señal = AdvertiseData.Builder().addServiceUuid(PracticaBle.servicio).build()
                    val datos = AdvertiseData.Builder()
                        .addServiceData(PracticaBle.servicio, PracticaBle.nombreParaAnuncio(nombre))
                        .build()
                    anuncio = anuncioActual
                    try {
                        anunciante.startAdvertising(settings, señal, datos, anuncioActual)
                    } catch (_: IllegalStateException) {
                        fallar()
                    } catch (_: SecurityException) {
                        fallar()
                    }
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic,
            ) {
                principal.post {
                    val abierto = servidor?.takeIf { sesion === this } ?: return@post
                    val estadoGatt = when {
                        characteristic.uuid != PracticaBle.identidadVendedor -> BluetoothGatt.GATT_FAILURE
                        offset !in 0..identidad.size -> BluetoothGatt.GATT_INVALID_OFFSET
                        else -> BluetoothGatt.GATT_SUCCESS
                    }
                    val datos = if (estadoGatt == BluetoothGatt.GATT_SUCCESS) {
                        identidad.copyOfRange(offset, identidad.size)
                    } else null
                    try {
                        abierto.sendResponse(device, requestId, estadoGatt, offset, datos)
                    } catch (_: SecurityException) {
                        fallar()
                    }
                }
            }
        }
        sesion = callback
        publicidad = anunciante
        val manager = app.getSystemService(BluetoothManager::class.java)
        val servicioGatt = BluetoothGattService(PracticaBle.servicio.uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        servicioGatt.addCharacteristic(
            BluetoothGattCharacteristic(
                PracticaBle.identidadVendedor,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ),
        )
        estado("Iniciando anuncio BLE…")
        try {
            servidor = manager?.openGattServer(app, callback)
            if (servidor?.addService(servicioGatt) != true) {
                fallar()
                return
            }
        } catch (_: SecurityException) {
            fallar()
            return
        }
        espera = Runnable { fallar() }.also { principal.postDelayed(it, 10_000L) }
    }

    fun detener() {
        espera?.let(principal::removeCallbacks)
        espera = null
        val anuncioAnterior = anuncio
        anuncio = null
        sesion = null
        try {
            anuncioAnterior?.let { publicidad?.stopAdvertising(it) }
        } catch (_: SecurityException) {
            // Android puede revocar el permiso mientras está abierto el diálogo.
        }
        publicidad = null
        try {
            servidor?.close()
        } catch (_: SecurityException) {
            // El servidor se descarta aunque el permiso haya sido revocado.
        }
        servidor = null
    }
}
