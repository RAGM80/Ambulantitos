package com.ambulantitos.core.data.firebase

import com.ambulantitos.core.domain.EstadoProducto
import com.ambulantitos.core.domain.FormasPago
import com.ambulantitos.core.domain.IconoProducto
import com.ambulantitos.core.domain.Order
import com.ambulantitos.core.domain.OrderItem
import com.ambulantitos.core.domain.Product
import com.ambulantitos.core.domain.RolUsuario
import com.ambulantitos.core.domain.Ubicacion
import com.ambulantitos.core.domain.User
import com.ambulantitos.core.domain.Vendor
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

internal fun User.aMapaFirestore(): Map<String, Any?> {
    val datos = mutableMapOf<String, Any?>(
        "userId" to userId,
        "role" to role,
        "nombres" to nombres,
        "apellidoPaterno" to apellidoPaterno,
        "apellidoMaterno" to apellidoMaterno,
        "fechaNacimiento" to fechaNacimiento,
        "usuario" to usuario,
    )
    if (role == RolUsuario.CLIENTE) {
        datos["numeroCelular"] = numeroCelular.orEmpty()
    } else {
        datos["nombreNegocio"] = nombreNegocio.orEmpty()
        datos["vendorId"] = vendorId.orEmpty()
    }
    return datos
}

internal fun DocumentSnapshot.aUser(): User? {
    if (!exists()) return null
    val role = string("role") ?: RolUsuario.CLIENTE
    return User(
        userId = string("userId") ?: id,
        role = role,
        nombres = string("nombres").orEmpty(),
        apellidoPaterno = string("apellidoPaterno").orEmpty(),
        apellidoMaterno = string("apellidoMaterno").orEmpty(),
        fechaNacimiento = string("fechaNacimiento").orEmpty(),
        usuario = string("usuario").orEmpty(),
        numeroCelular = if (role == RolUsuario.CLIENTE) string("numeroCelular") else null,
        nombreNegocio = if (role == RolUsuario.VENDEDOR) string("nombreNegocio") else null,
        vendorId = if (role == RolUsuario.VENDEDOR) string("vendorId") else null,
    )
}

internal fun Vendor.aMapaFirestore(): Map<String, Any> = mapOf(
    "vendorId" to vendorId,
    "ownerUserId" to ownerUserId,
    "nombreNegocio" to nombreNegocio,
    "descripcion" to descripcion,
    "categoriaPrincipal" to categoriaPrincipal,
    "modoLocalP2PActivo" to modoLocalP2PActivo,
    "transmitirUbicacion" to transmitirUbicacion,
    "ubicacion" to mapOf(
        "lat" to ubicacion.lat,
        "lng" to ubicacion.lng,
    ),
    "formaPagoPorDefecto" to FormasPago.EFECTIVO_AL_ENTREGAR,
    "contadorPedidos" to contadorPedidos,
)

internal fun DocumentSnapshot.aVendor(): Vendor? {
    if (!exists()) return null
    val ubicacionMapa = get("ubicacion") as? Map<*, *>
    return Vendor(
        vendorId = string("vendorId") ?: id,
        ownerUserId = string("ownerUserId").orEmpty(),
        nombreNegocio = string("nombreNegocio").orEmpty(),
        descripcion = string("descripcion").orEmpty(),
        categoriaPrincipal = string("categoriaPrincipal").orEmpty(),
        modoLocalP2PActivo = boolean("modoLocalP2PActivo"),
        transmitirUbicacion = boolean("transmitirUbicacion"),
        ubicacion = Ubicacion(
            lat = (ubicacionMapa?.get("lat") as? Number)?.toDouble() ?: 0.0,
            lng = (ubicacionMapa?.get("lng") as? Number)?.toDouble() ?: 0.0,
        ),
        formaPagoPorDefecto = string("formaPagoPorDefecto") ?: FormasPago.EFECTIVO_AL_ENTREGAR,
        contadorPedidos = (get("contadorPedidos") as? Number)?.toInt() ?: 0,
    )
}

internal fun Product.aMapaFirestore(): Map<String, Any?> {
    val ingredientesLimitados = ingredientes
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(6)
    val existenciasSeguras = existencias.coerceAtLeast(0)
    val estadoCalculado = when {
        existenciasSeguras <= 0 -> EstadoProducto.AGOTADO
        estado == EstadoProducto.AGOTADO -> EstadoProducto.AGOTADO
        else -> EstadoProducto.EN_EXISTENCIA
    }
    return mapOf(
        "productId" to productId,
        "nombre" to nombre,
        "iconoClave" to IconoProducto.normalizar(iconoClave),
        "unidadMedida" to unidadMedida.ifBlank { "pieza" },
        "precio" to precio,
        "existencias" to existenciasSeguras,
        "estado" to estadoCalculado,
        "activo" to activo,
        "descripcion" to descripcion,
        "marca" to marca,
        "ingredientes" to ingredientesLimitados,
    )
}

internal fun DocumentSnapshot.aProduct(): Product? {
    if (!exists()) return null
    val ingredientesCrudos = get("ingredientes") as? List<*>
    return Product(
        productId = string("productId") ?: id,
        nombre = string("nombre").orEmpty(),
        iconoClave = IconoProducto.normalizar(string("iconoClave")),
        unidadMedida = string("unidadMedida") ?: "pieza",
        precio = (get("precio") as? Number)?.toDouble() ?: 0.0,
        existencias = (get("existencias") as? Number)?.toInt() ?: 0,
        estado = string("estado") ?: EstadoProducto.EN_EXISTENCIA,
        activo = boolean("activo", predeterminado = true),
        descripcion = string("descripcion").orEmpty(),
        marca = string("marca"),
        ingredientes = ingredientesCrudos
            ?.mapNotNull { it as? String }
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(6)
            .orEmpty(),
    )
}

internal fun OrderItem.aMapaFirestore(): Map<String, Any> = mapOf(
    "productId" to productId,
    "producto" to producto,
    "iconoClave" to IconoProducto.normalizar(iconoClave),
    "cantidad" to cantidad,
    "indicaciones" to indicaciones,
    "precioUnitario" to precioUnitario,
)

@Suppress("UNCHECKED_CAST")
internal fun Order.aMapaFirestore(): Map<String, Any> = mapOf(
    "orderId" to orderId,
    "numeroPedido" to numeroPedido,
    "clientId" to clientId,
    "clienteNombre" to clienteNombre,
    "vendorId" to vendorId,
    "vendedorNombre" to vendedorNombre,
    "items" to items.map { it.aMapaFirestore() },
    "total" to total,
    "formaPago" to FormasPago.EFECTIVO_AL_ENTREGAR,
    "estado" to estado,
    "estadoEnvio" to estadoEnvio,
    "modoLocalP2P" to modoLocalP2P,
    "horaCreacion" to horaCreacion,
    "createdAt" to Timestamp(createdAt / 1000, ((createdAt % 1000) * 1_000_000).toInt()),
)

internal fun DocumentSnapshot.aOrder(): Order? {
    if (!exists()) return null
    val itemsCrudos = get("items") as? List<*>
    val createdAtMillis = when (val valor = get("createdAt")) {
        is Timestamp -> valor.toDate().time
        is Number -> valor.toLong()
        else -> 0L
    }
    return Order(
        orderId = string("orderId") ?: id,
        numeroPedido = (get("numeroPedido") as? Number)?.toInt() ?: 0,
        clientId = string("clientId").orEmpty(),
        clienteNombre = string("clienteNombre").orEmpty(),
        vendorId = string("vendorId").orEmpty(),
        vendedorNombre = string("vendedorNombre").orEmpty(),
        items = itemsCrudos
            ?.mapNotNull { it as? Map<*, *> }
            ?.map { mapa ->
                OrderItem(
                    productId = mapa["productId"] as? String ?: "",
                    producto = mapa["producto"] as? String ?: "",
                    iconoClave = IconoProducto.normalizar(mapa["iconoClave"] as? String),
                    cantidad = (mapa["cantidad"] as? Number)?.toInt() ?: 1,
                    indicaciones = mapa["indicaciones"] as? String ?: "",
                    precioUnitario = (mapa["precioUnitario"] as? Number)?.toDouble() ?: 0.0,
                )
            }
            .orEmpty(),
        total = (get("total") as? Number)?.toDouble() ?: 0.0,
        formaPago = string("formaPago") ?: FormasPago.EFECTIVO_AL_ENTREGAR,
        estado = string("estado").orEmpty(),
        estadoEnvio = string("estadoEnvio").orEmpty(),
        modoLocalP2P = boolean("modoLocalP2P"),
        horaCreacion = string("horaCreacion").orEmpty(),
        createdAt = createdAtMillis,
    )
}

private fun DocumentSnapshot.string(campo: String): String? = getString(campo)

private fun DocumentSnapshot.boolean(campo: String, predeterminado: Boolean = false): Boolean =
    getBoolean(campo) ?: predeterminado
