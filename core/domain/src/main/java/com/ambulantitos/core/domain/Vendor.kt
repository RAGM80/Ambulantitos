package com.ambulantitos.core.domain

data class Ubicacion(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

data class Vendor(
    val vendorId: String = "",
    val ownerUserId: String = "",
    val nombreNegocio: String = "",
    val descripcion: String = "",
    val categoriaPrincipal: String = "",
    val modoLocalP2PActivo: Boolean = false,
    val transmitirUbicacion: Boolean = false,
    val ubicacion: Ubicacion = Ubicacion(),
    val formaPagoPorDefecto: String = FormasPago.EFECTIVO_AL_ENTREGAR,
    val contadorPedidos: Int = 0,
)
