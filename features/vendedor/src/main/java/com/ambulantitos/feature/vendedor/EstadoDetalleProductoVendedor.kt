package com.ambulantitos.feature.vendedor

import com.ambulantitos.core.domain.Product

sealed interface EstadoDetalleProductoVendedor {
    data object Cargando : EstadoDetalleProductoVendedor
    data class Disponible(val producto: Product, val procesando: Boolean = false) : EstadoDetalleProductoVendedor
    data class Error(val mensaje: String) : EstadoDetalleProductoVendedor
    data object Eliminado : EstadoDetalleProductoVendedor
    data object SinSesion : EstadoDetalleProductoVendedor
}
