package com.ambulantitos.feature.vendedor

import com.ambulantitos.core.domain.User

sealed interface EstadoLoginVendedor {

    data object Inicial : EstadoLoginVendedor

    data object Cargando : EstadoLoginVendedor

    data class Completado(
        val usuario: User
    ) : EstadoLoginVendedor

    data class Error(
        val mensaje: String
    ) : EstadoLoginVendedor
}
