package com.ambulantitos.core.domain

object ColeccionesFirestore {
    const val USERS = "users"
    const val VENDORS = "vendors"
    const val PRODUCTS = "products"
    const val ORDERS = "orders"
    const val USERNAMES = "usernames"
}

object RolUsuario {
    const val CLIENTE = "client"
    const val VENDEDOR = "vendor"
}

object FormasPago {
    const val EFECTIVO_AL_ENTREGAR = "Pago en efectivo al entregar"
}

object EstadoProducto {
    const val EN_EXISTENCIA = "En existencia"
    const val AGOTADO = "Agotado"
}

object EstadoPedido {
    const val PENDIENTE = "Pendiente"
    const val ACEPTADO = "Aceptado"
    const val RECHAZADO = "Rechazado"
    const val ENTREGADO = "Entregado"
}

object EstadoEnvio {
    const val CONFIRMADO = "confirmado"
    const val SIN_CONFIRMAR = "sin_confirmar"
}

object IconoProducto {
    const val ELOTE = "elote"
    const val ESQUITE = "esquite"
    const val TACO = "taco"
    const val HELADO = "helado"
    const val BEBIDA = "bebida"
    const val OTRO = "otro"

    val PERMITIDOS = setOf(ELOTE, ESQUITE, TACO, HELADO, BEBIDA, OTRO)

    fun normalizar(valor: String?): String {
        val clave = valor?.trim()?.lowercase().orEmpty()
        return if (clave in PERMITIDOS) clave else OTRO
    }
}
