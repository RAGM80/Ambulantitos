package com.ambulantitos.core.network.p2p

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

object PruebaP2p {
    private const val PUERTO = 39871
    private const val VERSION = 1
    private const val FIRMA = 0x414D4250
    private const val CONFIRMACION = 0x41434B31
    const val MENSAJE = "Hola vendedor"

    suspend fun escuchar(
        direccion: InetAddress,
        listo: () -> Unit,
        recibido: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val mensajeEsperado = MENSAJE.toByteArray(Charsets.UTF_8)
        ServerSocket().use { servidor ->
            servidor.reuseAddress = true
            servidor.bind(InetSocketAddress(direccion, PUERTO))
            servidor.soTimeout = 750
            withContext(Dispatchers.Main) { listo() }
            while (true) {
                currentCoroutineContext().ensureActive()
                val cliente = try {
                    servidor.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                }
                cliente.use {
                    try {
                        it.soTimeout = 3_000
                        val entrada = DataInputStream(it.getInputStream())
                        if (entrada.readInt() != FIRMA || entrada.readUnsignedByte() != VERSION) return@use
                        val mensaje = ByteArray(mensajeEsperado.size)
                        entrada.readFully(mensaje)
                        if (!mensaje.contentEquals(mensajeEsperado)) return@use
                        withContext(Dispatchers.Main) { recibido(MENSAJE) }
                        val salida = DataOutputStream(it.getOutputStream())
                        salida.writeInt(CONFIRMACION)
                        salida.flush()
                    } catch (_: IOException) {
                        // Un cliente desconectado no interrumpe el servidor del vendedor.
                    }
                }
            }
        }
    }

    suspend fun enviar(direccion: InetAddress): Unit = withContext(Dispatchers.IO) {
        Socket().use { cliente ->
            cliente.connect(InetSocketAddress(direccion, PUERTO), 5_000)
            cliente.soTimeout = 5_000
            val salida = DataOutputStream(cliente.getOutputStream())
            salida.writeInt(FIRMA)
            salida.writeByte(VERSION)
            salida.write(MENSAJE.toByteArray(Charsets.UTF_8))
            salida.flush()
            val entrada = DataInputStream(cliente.getInputStream())
            if (entrada.readInt() != CONFIRMACION) {
                throw IOException("El vendedor no confirmó el mensaje P2P.")
            }
        }
    }
}
