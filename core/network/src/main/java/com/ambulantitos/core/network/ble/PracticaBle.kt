package com.ambulantitos.core.network.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import java.util.UUID

object PracticaBle {
    internal val servicio = ParcelUuid.fromString("18c2e952-e699-4733-93a3-bba156933cf1")
    internal val identidadVendedor = UUID.fromString("53ddceb6-fc70-43d1-852f-30a6310e4ff4")
    internal val formatoVendorId = Regex("[A-Za-z0-9_-]{1,128}")

    fun permisos(vendedor: Boolean): Array<String> = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        if (vendedor) Manifest.permission.BLUETOOTH_ADVERTISE else Manifest.permission.BLUETOOTH_SCAN,
    )

    fun tienePermisos(context: Context, vendedor: Boolean): Boolean =
        permisos(vendedor).all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    internal fun adaptador(context: Context) = context.getSystemService(BluetoothManager::class.java)?.adapter

    internal fun nombreParaAnuncio(nombre: String): ByteArray {
        // La respuesta al escaneo tiene 31 bytes, incluidos 18 de cabecera y UUID.
        val maxBytes = 13
        var fin = nombre.length.coerceAtMost(maxBytes)
        while (fin > 0) {
            if (!Character.isHighSurrogate(nombre[fin - 1])) {
                val bytes = nombre.substring(0, fin).toByteArray(Charsets.UTF_8)
                if (bytes.size <= maxBytes) return bytes
            }
            fin--
        }
        return byteArrayOf()
    }
}
