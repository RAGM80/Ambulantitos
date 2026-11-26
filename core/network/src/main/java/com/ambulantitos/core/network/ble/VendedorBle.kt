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

    fun iniciar(nombre: String, estado: (String) -> Unit, recibido: (String) -> Unit) {
        detener()
        val adapter = PracticaBle.adaptador(app)
        val anunciante = adapter?.takeIf { it.isEnabled }?.bluetoothLeAdvertiser
        if (anunciante == null) {
            estado("Activa Bluetooth. El teléfono vendedor debe admitir anuncios BLE.")
            return
        }
        fun fallar() {
            detener()
            estado("No se pudo iniciar el anuncio BLE. Vuelve a abrir la práctica.")
        }
        val anuncioActual = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                if (anuncio !== this) return
                espera?.let(principal::removeCallbacks)
                espera = null
                estado("Anuncio BLE activo. Esperando un mensaje del cliente.")
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

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray,
            ) {
                principal.post {
                    val abierto = servidor?.takeIf { sesion === this } ?: return@post
                    val valido = characteristic.uuid == PracticaBle.mensaje && !preparedWrite &&
                        offset == 0 && value.size in 1..PracticaBle.MAX_BYTES_MENSAJE
                    val estadoGatt = if (valido) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
                    try {
                        if (responseNeeded) abierto.sendResponse(device, requestId, estadoGatt, 0, null)
                        if (valido) recibido(value.toString(Charsets.UTF_8))
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
                PracticaBle.mensaje,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
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
