package com.ambulantitos.core.domain

data class Product(
    val productId: String = "",
    val nombre: String = "",
    val iconoClave: String = IconoProducto.OTRO,
    val unidadMedida: String = "pieza",
    val precio: Double = 0.0,
    val existencias: Int = 0,
    val estado: String = EstadoProducto.EN_EXISTENCIA,
    val activo: Boolean = true,
    val descripcion: String = "",
    val marca: String? = null,
    val ingredientes: List<String> = emptyList(),
) {
    val visibleEnCatalogoCliente: Boolean
        get() = activo &&
            estado == EstadoProducto.EN_EXISTENCIA &&
            existencias > 0
}
