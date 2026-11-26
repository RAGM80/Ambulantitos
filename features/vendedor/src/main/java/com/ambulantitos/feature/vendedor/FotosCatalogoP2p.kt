package com.ambulantitos.feature.vendedor

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ambulantitos.core.domain.Product
import com.ambulantitos.core.network.p2p.CatalogoP2p
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

internal suspend fun cargarFotosCatalogo(
    contexto: Context,
    productos: List<Product>,
): Map<String, ByteArray> = withContext(Dispatchers.Default) {
    val fotos = mutableMapOf<String, ByteArray>()
    val cargador = SingletonImageLoader.get(contexto)
    val conFoto = productos.asSequence()
        .mapNotNull { producto -> CatalogoP2p.urlFotoCompartida(producto.imagenUrl)?.let { producto.productId to it } }
        .take(24)
    withTimeoutOrNull(3_500L) {
        for ((productId, url) in conFoto) {
            val solicitud = ImageRequest.Builder(contexto)
                .data(url)
                .size(144)
                .allowHardware(false)
                .build()
            val imagen = (cargador.execute(solicitud) as? SuccessResult)?.image ?: continue
            val miniatura = imagen.toBitmap(imagen.width.coerceIn(1, 144), imagen.height.coerceIn(1, 144))
            val salida = ByteArrayOutputStream()
            if (miniatura.compress(Bitmap.CompressFormat.WEBP_LOSSY, 70, salida) &&
                salida.size() in 1..CatalogoP2p.MAX_MINIATURA_BYTES
            ) {
                fotos[productId] = salida.toByteArray()
            }
        }
    }
    fotos
}
