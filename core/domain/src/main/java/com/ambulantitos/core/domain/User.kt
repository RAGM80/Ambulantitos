package com.ambulantitos.core.domain

data class User(
    val userId: String = "",
    val role: String = RolUsuario.CLIENTE,
    val nombres: String = "",
    val apellidoPaterno: String = "",
    val apellidoMaterno: String = "",
    val fechaNacimiento: String = "",
    val usuario: String = "",
    val numeroCelular: String? = null,
    val nombreNegocio: String? = null,
    val vendorId: String? = null,
) {
    val esCliente: Boolean get() = role == RolUsuario.CLIENTE
    val esVendedor: Boolean get() = role == RolUsuario.VENDEDOR

    val nombreCompleto: String
        get() = listOf(nombres, apellidoPaterno, apellidoMaterno)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}
