package com.ambulantitos.core.domain

data class Order(
    val orderId: String = "",
    val numeroPedido: Int = 0,
    val clientId: String = "",
    val clienteNombre: String = "",
    val vendorId: String = "",
    val vendedorNombre: String = "",
    val items: List<OrderItem> = emptyList(),
    val total: Double = 0.0,
    val formaPago: String = FormasPago.EFECTIVO_AL_ENTREGAR,
    val estado: String = EstadoPedido.PENDIENTE,
    val estadoEnvio: String = EstadoEnvio.SIN_CONFIRMAR,
    val modoLocalP2P: Boolean = false,
    val horaCreacion: String = "",
    val createdAt: Long = 0L,
) {
    val envioSinConfirmar: Boolean get() = estadoEnvio == EstadoEnvio.SIN_CONFIRMAR
}
