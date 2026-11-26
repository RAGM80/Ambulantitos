package com.ambulantitos.feature.vendedor

import com.ambulantitos.core.domain.User

sealed interface EstadoPanelVendedor {

    data object Cargando : EstadoPanelVendedor

    data class Completado(
        val usuario: User
    ) : EstadoPanelVendedor

    data object SinSesion : EstadoPanelVendedor

    data class Error(
        val mensaje: String
    ) : EstadoPanelVendedor
}