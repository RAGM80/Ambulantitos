package com.ambulantitos.feature.vendedor

import com.ambulantitos.core.domain.Product

sealed interface EstadoProductosVendedor {

    data object Cargando : EstadoProductosVendedor

    data class Completado(
        val productos: List<Product>
    ) : EstadoProductosVendedor

    data object SinSesion : EstadoProductosVendedor

    data class Error(
        val mensaje: String
    ) : EstadoProductosVendedor
}