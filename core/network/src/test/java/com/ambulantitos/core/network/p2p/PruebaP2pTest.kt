package com.ambulantitos.core.network.p2p

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

@OptIn(ExperimentalCoroutinesApi::class)
class PruebaP2pTest {
    @Test
    fun entregaElMensajeYEsperaConfirmacion() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val direccion = InetAddress.getLoopbackAddress()
        val listo = CompletableDeferred<Unit>()
        val recibido = CompletableDeferred<String>()
        val servidor = launch {
            PruebaP2p.escuchar(
                direccion,
                listo = { listo.complete(Unit) },
                recibido = { recibido.complete(it) },
            )
        }
        try {
            withTimeout(8_000) {
                listo.await()
                PruebaP2p.enviar(direccion)
                assertEquals("Hola vendedor", recibido.await())
            }
        } finally {
            servidor.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }
}
