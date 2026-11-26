package com.ambulantitos.core.network.p2p

import com.ambulantitos.core.domain.Product
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogoP2pTest {
    @Test
    fun elCatalogoCorrespondeAlVendorIdSolicitado() = runBlocking {
        val direccion = InetAddress.getLoopbackAddress()
        val producto = Product(
            productId = "p1",
            nombre = "Esquite",
            precio = 30.0,
            existencias = 4,
            imagenUrl = "https://res.cloudinary.com/prueba/image/upload/esquite.webp",
        )
        val agotado = producto.copy(productId = "p2", existencias = 0)
        val miniatura = byteArrayOf(41, 42, 43)
        val bytes = CatalogoP2p.codificar(
            CatalogoP2p.Catalogo("negocio1", "Elotes", listOf(producto, agotado), mapOf("p1" to miniatura)),
        )
        Dispatchers.setMain(Dispatchers.Unconfined)
        val listo = CompletableDeferred<Unit>()
        val servidor = launch {
            CatalogoP2p.escuchar(direccion, "negocio1", { bytes }, { listo.complete(Unit) })
        }
        try {
            withTimeout(8_000) {
                listo.await()
                val otro = runCatching { CatalogoP2p.pedir(direccion, "negocio2") }
                assertTrue(otro.exceptionOrNull() is CatalogoP2p.OtroVendedor)
                val catalogo = CatalogoP2p.decodificar(CatalogoP2p.pedir(direccion, "negocio1"))
                assertEquals("negocio1", catalogo.vendorId)
                assertEquals("Elotes", catalogo.nombre)
                assertEquals(listOf(producto), catalogo.productos)
                assertArrayEquals(miniatura, catalogo.miniaturas["p1"])

                val protocoloAnterior = 0x414D4301
                Socket(direccion, 39871).use {
                    it.soTimeout = 2_000
                    val salida = DataOutputStream(it.getOutputStream())
                    salida.writeInt(protocoloAnterior)
                    salida.writeUTF("negocio1")
                    salida.flush()
                    val entrada = DataInputStream(it.getInputStream())
                    assertEquals(protocoloAnterior, entrada.readInt())
                    assertEquals(0, entrada.readUnsignedByte())
                    val legado = ByteArray(entrada.readInt()).also(entrada::readFully)
                    val catalogoLegado = CatalogoP2p.decodificar(legado)
                    assertEquals(listOf(producto.copy(imagenUrl = null)), catalogoLegado.productos)
                    assertTrue(catalogoLegado.miniaturas.isEmpty())
                }
            }
        } finally {
            servidor.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }
}
