package com.ambulantitos.core.network.p2p

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.MacAddress
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetAddress
import kotlin.coroutines.resume

@MainThread
@SuppressLint("MissingPermission") // Cada pantalla solicita el permiso antes de abrir una sesión.
class WifiDirect(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val manager = app.getSystemService(WifiP2pManager::class.java)
    private val principal = Handler(Looper.getMainLooper())
    private var canal: WifiP2pManager.Channel? = null
    private var buscando = false
    private var grupoSolicitado = false

    data class Dispositivo(val nombre: String, val direccion: String)
    class Error(mensaje: String) : IOException(mensaje)

    fun abrir(paraBusqueda: Boolean) {
        if (!tienePermiso(app)) throw Error("Permite el acceso a dispositivos cercanos.")
        if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            throw Error("Este teléfono no admite Wi-Fi Direct.")
        }
        if (app.getSystemService(WifiManager::class.java)?.isWifiEnabled != true) {
            throw Error("Enciende el Wi-Fi para usar Wi-Fi Direct.")
        }
        if (paraBusqueda && app.getSystemService(LocationManager::class.java)?.isLocationEnabled != true) {
            throw Error("Activa Ubicación para buscar dispositivos por Wi-Fi Direct.")
        }
        canal = manager?.initialize(app, Looper.getMainLooper(), null)
            ?: throw Error("No se pudo iniciar Wi-Fi Direct.")
    }

    suspend fun nombreDelTelefono(): String? = withTimeoutOrNull(2_000L) {
        suspendCancellableCoroutine { espera ->
            servicio().requestDeviceInfo(canalActual()) {
                if (espera.isActive) espera.resume(it?.deviceName)
            }
        }
    }

    suspend fun crearGrupo(): InetAddress {
        verificarSinGrupo()
        grupoSolicitado = true
        ejecutar("crear el grupo") { servicio().createGroup(canalActual(), it) }
        return esperarGrupo(debeSerPropietario = true)
    }

    suspend fun buscar(actualizar: (List<Dispositivo>) -> Unit) {
        verificarSinGrupo()
        buscando = true
        try {
            ejecutar("buscar dispositivos") { servicio().discoverPeers(canalActual(), it) }
            repeat(10) {
                delay(1_000L)
                val dispositivos = withTimeoutOrNull(2_000L) {
                    suspendCancellableCoroutine { espera ->
                        servicio().requestPeers(canalActual()) { lista ->
                            if (espera.isActive) {
                                espera.resume(lista.deviceList
                                    .filter { it.status != WifiP2pDevice.UNAVAILABLE }
                                    .map { Dispositivo(it.deviceName, it.deviceAddress) }
                                    .sortedBy { it.nombre })
                            }
                        }
                    }
                }
                if (dispositivos != null) actualizar(dispositivos)
            }
        } finally {
            detenerBusqueda()
        }
    }

    suspend fun conectar(dispositivo: Dispositivo): InetAddress {
        verificarSinGrupo()
        val config = WifiP2pConfig.Builder()
            .setDeviceAddress(MacAddress.fromString(dispositivo.direccion))
            .build()
        grupoSolicitado = true
        ejecutar("solicitar la conexión") { servicio().connect(canalActual(), config, it) }
        return esperarGrupo(debeSerPropietario = false)
    }

    private suspend fun verificarSinGrupo() {
        val conexion = withTimeoutOrNull(3_000L) { consultarConexion() }
            ?: throw Error("Android no informó el estado de Wi-Fi Direct.")
        if (conexion.groupFormed) {
            throw Error("Ya hay un grupo Wi-Fi Direct. Sal de ese grupo e intenta de nuevo.")
        }
    }

    private suspend fun esperarGrupo(debeSerPropietario: Boolean): InetAddress {
        val conexion = withTimeoutOrNull(30_000L) {
            var info = consultarConexion()
            while (!info.groupFormed || info.groupOwnerAddress == null) {
                delay(700L)
                info = consultarConexion()
            }
            info
        } ?: throw Error("La conexión no se completó. Revisa la invitación de Wi-Fi Direct.")
        if (conexion.isGroupOwner != debeSerPropietario) {
            throw Error("El vendedor debe abrir el grupo Wi-Fi Direct primero.")
        }
        return conexion.groupOwnerAddress
    }

    private suspend fun consultarConexion(): WifiP2pInfo = suspendCancellableCoroutine { espera ->
        servicio().requestConnectionInfo(canalActual()) {
            if (espera.isActive) espera.resume(it)
        }
    }

    private suspend fun ejecutar(operacion: String, llamada: (WifiP2pManager.ActionListener) -> Unit) {
        val exito = withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine { espera ->
                llamada(object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        if (espera.isActive) espera.resume(true)
                    }

                    override fun onFailure(reason: Int) {
                        if (espera.isActive) espera.resume(false)
                    }
                })
            }
        }
        if (exito != true) throw Error("No se pudo $operacion por Wi-Fi Direct. Intenta de nuevo.")
    }

    private fun detenerBusqueda() {
        if (!buscando) return
        buscando = false
        val actual = canal ?: return
        runCatching { manager?.stopPeerDiscovery(actual, null) }
    }

    override fun close() {
        detenerBusqueda()
        val actual = canal ?: return
        canal = null
        if (!grupoSolicitado) {
            actual.close()
            return
        }
        grupoSolicitado = false
        val cerrarCanal = Runnable { actual.close() }
        val retirada = object : WifiP2pManager.ActionListener {
            override fun onSuccess() = terminar()
            override fun onFailure(reason: Int) = terminar()

            private fun terminar() {
                principal.removeCallbacks(cerrarCanal)
                cerrarCanal.run()
            }
        }
        principal.postDelayed(cerrarCanal, 3_000L)
        try {
            servicio().cancelConnect(actual, null)
            servicio().removeGroup(actual, retirada)
        } catch (_: SecurityException) {
            principal.removeCallbacks(cerrarCanal)
            cerrarCanal.run()
        }
    }

    private fun canalActual() = canal ?: throw Error("La sesión de Wi-Fi Direct se cerró.")
    private fun servicio() = manager ?: throw Error("Wi-Fi Direct no está disponible.")

    companion object {
        const val PERMISO = Manifest.permission.NEARBY_WIFI_DEVICES

        fun tienePermiso(context: Context): Boolean =
            context.checkSelfPermission(PERMISO) == PackageManager.PERMISSION_GRANTED
    }
}
