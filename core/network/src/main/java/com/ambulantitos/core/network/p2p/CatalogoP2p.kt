package com.ambulantitos.core.network.p2p

import com.ambulantitos.core.domain.IconoProducto
import com.ambulantitos.core.domain.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI

object CatalogoP2p {
    private const val PUERTO = 39871
    private const val PROTOCOLO_ORIGINAL = 0x414D4301
    private const val PROTOCOLO_FOTOS = 0x414D4302
    private const val MAX_PRODUCTOS = 1_000
    private const val MAX_BYTES = 192 * 1024
    private const val MAX_URL_BYTES = 2_048
    const val MAX_MINIATURA_BYTES = 8 * 1024
    private const val DISPONIBLE = 0
    private const val OTRO_VENDEDOR = 1
    private const val SIN_CATALOGO = 2

    data class Catalogo(
        val vendorId: String,
        val nombre: String,
        val productos: List<Product>,
        val miniaturas: Map<String, ByteArray> = emptyMap(),
    )

    class OtroVendedor : IOException()
    class SinCatalogo : IOException()

    suspend fun escuchar(
        direccion: InetAddress,
        vendorId: String,
        contenido: () -> ByteArray?,
        listo: () -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        ServerSocket().use { servidor ->
            servidor.reuseAddress = true
            servidor.bind(InetSocketAddress(direccion, PUERTO))
            servidor.soTimeout = 500
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
                        it.soTimeout = 2_000
                        val entrada = DataInputStream(it.getInputStream())
                        val protocolo = entrada.readInt()
                        if (protocolo != PROTOCOLO_FOTOS && protocolo != PROTOCOLO_ORIGINAL) return@use
                        val pedido = entrada.readUTF()
                        val salida = DataOutputStream(it.getOutputStream())
                        salida.writeInt(protocolo)
                        if (pedido != vendorId) {
                            salida.writeByte(OTRO_VENDEDOR)
                        } else {
                            val catalogo = withContext(Dispatchers.Main) { contenido() }
                            val bytes = if (catalogo != null && protocolo == PROTOCOLO_ORIGINAL) {
                                codificar(decodificar(catalogo), incluirFotos = false)
                            } else catalogo
                            if (bytes == null) {
                                salida.writeByte(SIN_CATALOGO)
                            } else {
                                salida.writeByte(DISPONIBLE)
                                salida.writeInt(bytes.size)
                                salida.write(bytes)
                            }
                        }
                        salida.flush()
                    } catch (_: IOException) {
                        // Una solicitud incompleta sale sin interrumpir el servicio.
                    }
                }
            }
        }
    }

    suspend fun pedir(direccion: InetAddress, vendorId: String): ByteArray = withContext(Dispatchers.IO) {
        try {
            pedir(direccion, vendorId, PROTOCOLO_FOTOS)
        } catch (_: EOFException) {
            pedir(direccion, vendorId, PROTOCOLO_ORIGINAL)
        } catch (_: SocketException) {
            pedir(direccion, vendorId, PROTOCOLO_ORIGINAL)
        }
    }

    private fun pedir(direccion: InetAddress, vendorId: String, protocolo: Int): ByteArray =
        Socket().use { cliente ->
            cliente.connect(InetSocketAddress(direccion, PUERTO), 5_000)
            cliente.soTimeout = 5_000
            val salida = DataOutputStream(cliente.getOutputStream())
            salida.writeInt(protocolo)
            salida.writeUTF(vendorId)
            salida.flush()

            val entrada = DataInputStream(cliente.getInputStream())
            if (entrada.readInt() != protocolo) throw IOException("Versión de catálogo desconocida.")
            when (entrada.readUnsignedByte()) {
                OTRO_VENDEDOR -> throw OtroVendedor()
                SIN_CATALOGO -> throw SinCatalogo()
                DISPONIBLE -> {
                    val tamano = entrada.readInt()
                    if (tamano !in 1..MAX_BYTES) throw IOException("Tamaño de catálogo inválido.")
                    ByteArray(tamano).also {
                        entrada.readFully(it)
                        if (decodificar(it).vendorId != vendorId) throw OtroVendedor()
                    }
                }
                else -> throw IOException("Respuesta de catálogo desconocida.")
            }
        }

    fun codificar(catalogo: Catalogo): ByteArray = codificar(catalogo, incluirFotos = true)

    private fun codificar(catalogo: Catalogo, incluirFotos: Boolean): ByteArray {
        val productos = catalogo.productos.filter { it.visibleEnCatalogoCliente }
        if (productos.size > MAX_PRODUCTOS) throw IOException("El catálogo tiene demasiados productos.")
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { salida ->
            salida.writeUTF(catalogo.vendorId)
            salida.writeUTF(catalogo.nombre)
            salida.writeInt(if (incluirFotos) -productos.size - 1 else productos.size)
            for (producto in productos) {
                salida.writeUTF(producto.productId)
                salida.writeUTF(producto.nombre)
                salida.writeUTF(producto.iconoClave)
                salida.writeDouble(producto.precio)
                salida.writeInt(producto.existencias)
                if (bytes.size() > MAX_BYTES) throw IOException("El catálogo supera el tamaño permitido.")
            }
            if (incluirFotos) {
                productos.forEachIndexed { indice, producto ->
                    val url = urlFotoCompartida(producto.imagenUrl)?.toByteArray(Charsets.UTF_8) ?: return@forEachIndexed
                    val libres = MAX_BYTES - bytes.size() - 3 * Int.SIZE_BYTES - url.size
                    if (libres < 0) return@forEachIndexed
                    val foto = catalogo.miniaturas[producto.productId]
                        ?.takeIf { it.size in 1..minOf(MAX_MINIATURA_BYTES, libres) }
                    salida.writeInt(indice)
                    salida.writeInt(url.size)
                    salida.write(url)
                    salida.writeInt(foto?.size ?: 0)
                    if (foto != null) salida.write(foto)
                }
            }
        }
        return bytes.toByteArray()
    }

    fun decodificar(bytes: ByteArray): Catalogo {
        if (bytes.size !in 1..MAX_BYTES) throw IOException("Tamaño de catálogo inválido.")
        DataInputStream(ByteArrayInputStream(bytes)).use { entrada ->
            val vendorId = entrada.readUTF()
            val nombre = entrada.readUTF()
            val longitud = entrada.readInt()
            val cantidad = if (longitud < 0) -(longitud + 1) else longitud
            if (vendorId.isBlank() || cantidad !in 0..MAX_PRODUCTOS) throw IOException("Catálogo incompleto.")
            val productos = MutableList(cantidad) {
                Product(
                    productId = entrada.readUTF(),
                    nombre = entrada.readUTF(),
                    iconoClave = IconoProducto.normalizar(entrada.readUTF()),
                    precio = entrada.readDouble(),
                    existencias = entrada.readInt(),
                ).also {
                    if (it.productId.isBlank() || it.nombre.isBlank() || !it.precio.isFinite() ||
                        it.precio < 0.0 || !it.visibleEnCatalogoCliente
                    ) throw IOException("Producto inválido.")
                }
            }
            val miniaturas = mutableMapOf<String, ByteArray>()
            if (longitud < 0) {
                val indices = mutableSetOf<Int>()
                while (entrada.available() > 0) {
                    val indice = entrada.readInt()
                    if (indice !in productos.indices || !indices.add(indice)) throw IOException("Foto duplicada o inválida.")
                    val urlLongitud = entrada.readInt()
                    if (urlLongitud !in 1..MAX_URL_BYTES) throw IOException("URL de foto inválida.")
                    val url = ByteArray(urlLongitud).also { entrada.readFully(it) }.toString(Charsets.UTF_8)
                    val producto = productos[indice]
                    productos[indice] = producto.copy(imagenUrl = urlFotoCompartida(url))
                    val fotoLongitud = entrada.readInt()
                    if (fotoLongitud !in 0..MAX_MINIATURA_BYTES) throw IOException("Miniatura inválida.")
                    if (fotoLongitud > 0) {
                        miniaturas[producto.productId] = ByteArray(fotoLongitud).also { entrada.readFully(it) }
                    }
                }
            }
            if (entrada.available() != 0) throw IOException("Catálogo inválido.")
            return Catalogo(vendorId, nombre, productos, miniaturas)
        }
    }

    fun urlFotoCompartida(url: String?): String? {
        if (url.isNullOrBlank() || url.toByteArray(Charsets.UTF_8).size > MAX_URL_BYTES) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return url.takeIf {
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("res.cloudinary.com", ignoreCase = true) &&
                uri.userInfo == null && uri.port == -1
        }
    }
}
