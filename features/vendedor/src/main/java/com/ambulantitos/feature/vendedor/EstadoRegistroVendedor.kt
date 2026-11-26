package com.ambulantitos.feature.vendedor

import com.ambulantitos.core.domain.User

sealed interface EstadoRegistroVendedor {

    data object Inicial : EstadoRegistroVendedor

    data object Cargando : EstadoRegistroVendedor

    data class Completado(
        val usuario: User
    ) : EstadoRegistroVendedor

    data class Error(
        val mensaje: String
    ) : EstadoRegistroVendedor
}
