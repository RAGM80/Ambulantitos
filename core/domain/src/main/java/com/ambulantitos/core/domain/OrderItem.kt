package com.ambulantitos.core.domain

data class OrderItem(
    val productId: String = "",
    val producto: String = "",
    val iconoClave: String = IconoProducto.OTRO,
    val cantidad: Int = 1,
    val indicaciones: String = "",
    val precioUnitario: Double = 0.0,
) {
    val subtotal: Double get() = precioUnitario * cantidad
}
