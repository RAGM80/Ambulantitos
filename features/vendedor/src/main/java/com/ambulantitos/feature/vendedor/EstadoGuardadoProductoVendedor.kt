package com.ambulantitos.feature.vendedor

import com.ambulantitos.core.domain.Product

sealed interface EstadoGuardadoProductoVendedor {

    data object Inicial : EstadoGuardadoProductoVendedor

    data object CargandoProducto : EstadoGuardadoProductoVendedor

    data object SubiendoImagen : EstadoGuardadoProductoVendedor

    data object GuardandoProducto : EstadoGuardadoProductoVendedor

    data class Completado(
        val producto: Product
    ) : EstadoGuardadoProductoVendedor

    data class Error(
        val mensaje: String
    ) : EstadoGuardadoProductoVendedor
}
