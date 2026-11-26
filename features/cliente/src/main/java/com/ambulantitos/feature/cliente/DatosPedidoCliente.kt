package com.ambulantitos.feature.cliente

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.widget.TextView
import coil3.load
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import com.ambulantitos.core.domain.Order
import com.ambulantitos.core.domain.OrderItem
import java.io.Serializable
import java.text.NumberFormat
import java.util.Locale

internal data class ProductoPedido(
    val vendorId: String,
    val negocio: String,
    val productId: String,
    val nombre: String,
    val iconoClave: String,
    val precio: Double,
    val existencias: Int,
    val imagenUrl: String?,
    val miniatura: ByteArray?,
) : Serializable {
    fun aItem(cantidad: Int, indicaciones: String) =
        OrderItem(productId, nombre, iconoClave, cantidad, indicaciones, precio)
}

internal const val EXTRA_PRODUCTO = "productoPedido"
internal const val EXTRA_CANTIDAD = "cantidadPedido"
internal const val EXTRA_INDICACIONES = "indicaciones"
internal const val EXTRA_ORDER_ID = "orderId"

internal fun dinero(importe: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")).format(importe)

internal fun ImageView.mostrarFotoProducto(imagenUrl: String?, miniatura: ByteArray?) {
    val fotoLocal = miniatura?.let { bytes ->
        val dimensiones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensiones)
        if (dimensiones.outWidth in 1..256 && dimensiones.outHeight in 1..256) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else null
    }
    val marcador = android.R.drawable.ic_menu_gallery
    val imagen: Any? = fotoLocal ?: imagenUrl
    load(imagen) {
        placeholder(marcador)
        fallback(marcador)
        error(marcador)
    }
}

internal fun Activity.texto(id: Int, valor: CharSequence) {
    findViewById<TextView>(id).text = valor
}

internal fun Context.tituloPedido(pedido: Order): String =
    getString(R.string.cliente_pedido_numero, pedido.numeroPedido)

internal fun Context.resumenPedido(pedido: Order): String =
    pedido.items.joinToString("\n") {
        getString(R.string.cliente_pedido_linea, it.cantidad, it.producto)
    }

internal fun Activity.volverABusqueda() {
    startActivity(Intent(this, BusquedaClienteActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    })
}
