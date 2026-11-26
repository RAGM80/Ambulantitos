package com.ambulantitos.core.database.firebase

import com.ambulantitos.core.domain.EstadoEnvio
import com.ambulantitos.core.domain.EstadoPedido
import com.ambulantitos.core.domain.EstadoProducto
import com.ambulantitos.core.domain.Order
import com.ambulantitos.core.domain.Product
import com.ambulantitos.core.domain.User
import com.ambulantitos.core.domain.Vendor
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FuenteFirestore {
    private val firestore by lazy {
        FirebaseAmbulantitos.activarCachePersistente()
        FirebaseFirestore.getInstance()
    }

    suspend fun obtenerUsuario(userId: String): User? =
        firestore.collection(USERS).document(userId).get().await().aUser()

    suspend fun guardarRegistro(perfil: User, negocio: Vendor?) {
        val usernameRef = firestore.collection(USERNAMES).document(perfil.usuario)
        val userRef = firestore.collection(USERS).document(perfil.userId)
        firestore.runTransaction { transaccion ->
            if (transaccion.get(usernameRef).exists()) {
                throw FirebaseFirestoreException(
                    "Ese nombre de usuario ya está ocupado.",
                    FirebaseFirestoreException.Code.ALREADY_EXISTS,
                )
            }
            transaccion.set(userRef, perfil.aMapaFirestore())
            transaccion.set(
                usernameRef,
                mapOf("userId" to perfil.userId, "role" to perfil.role, "usuario" to perfil.usuario),
            )
            if (negocio != null) {
                transaccion.set(vendedor(negocio.vendorId), negocio.aMapaFirestore())
            }
        }.await()
    }

    suspend fun guardarVendedor(perfil: Vendor) {
        vendedor(perfil.vendorId).set(perfil.aMapaFirestore(), SetOptions.merge()).await()
    }

    suspend fun obtenerVendedor(vendorId: String): Vendor? = vendedor(vendorId).get().await().aVendor()

    fun escucharProductosVendedor(vendorId: String): Flow<List<Product>> =
        productosActivos(vendorId).observar { it.aProductos() }

    fun escucharCatalogoCliente(vendorId: String): Flow<List<Product>> =
        catalogoCliente(vendorId).observar { it.aProductos() }

    suspend fun listarProductosVendedor(vendorId: String): List<Product> =
        productosActivos(vendorId).get().await().aProductos()

    suspend fun listarCatalogoCliente(vendorId: String): List<Product> =
        catalogoCliente(vendorId).get().await().aProductos()

    suspend fun obtenerProducto(vendorId: String, productId: String): Product? =
        productos(vendorId).document(productId).get().await().aProduct()

    fun escucharProducto(vendorId: String, productId: String): Flow<Product?> = callbackFlow {
        val registro = productos(vendorId).document(productId).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) close(error) else if (snapshot != null && !snapshot.metadata.hasPendingWrites()) trySend(snapshot.aProduct())
        }
        awaitClose { registro.remove() }
    }

    suspend fun crearProducto(vendorId: String, producto: Product): Product {
        val coleccion = productos(vendorId)
        val referencia = if (producto.productId.isBlank()) {
            coleccion.document()
        } else {
            coleccion.document(producto.productId)
        }
        val guardado = producto.copy(productId = referencia.id)
        referencia.set(guardado.aMapaFirestore()).await()
        return guardado
    }

    suspend fun editarProducto(
        vendorId: String,
        producto: Product,
        existenciasEsperadas: Int? = null
    ): Product {
        val referencia = productos(vendorId).document(producto.productId)
        return firestore.runTransaction { transaccion ->
            val actual = transaccion.get(referencia).aProduct()
                ?: error("El producto ya no existe.")
            verificarCondicion(actual.activo) { "El producto ya fue eliminado del catálogo." }
            verificarCondicion(existenciasEsperadas == null || actual.existencias == existenciasEsperadas) {
                "Las existencias cambiaron mientras editabas. Abre el producto de nuevo para actualizarlas."
            }
            val guardado = producto.copy(
                activo = actual.activo,
                estado = if (producto.existencias <= 0) EstadoProducto.AGOTADO else producto.estado
            )
            transaccion.set(referencia, guardado.aMapaFirestore(), SetOptions.merge())
            guardado
        }.await()
    }

    suspend fun marcarProductoAgotado(vendorId: String, productId: String) {
        productos(vendorId).document(productId)
            .update(mapOf("estado" to EstadoProducto.AGOTADO, "existencias" to 0)).await()
    }

    suspend fun desactivarProducto(vendorId: String, productId: String) {
        productos(vendorId).document(productId).update("activo", false).await()
    }

    suspend fun crearPedido(pedido: Order): Order {
        val vendorRef = vendedor(pedido.vendorId)
        val orderRef = firestore.collection(ORDERS).document()
        return firestore.runTransaction { transaccion ->
            val numeroPedido = (transaccion.get(vendorRef).getLong("contadorPedidos") ?: 0L).toInt() + 1
            val catalogo = productos(pedido.vendorId)
            val items = pedido.items.groupBy { it.productId }.flatMap { (id, lineas) ->
                val producto = transaccion.get(catalogo.document(id)).aProduct()
                verificarCondicion(
                    producto != null && producto.visibleEnCatalogoCliente &&
                        lineas.all { it.cantidad > 0 && it.precioUnitario == producto.precio } &&
                        lineas.sumOf { it.cantidad.toLong() } <= producto.existencias
                ) { "Cambió el precio o la disponibilidad de ${lineas.first().producto}. Vuelve al catálogo." }
                val actual = requireNotNull(producto)
                lineas.map { it.copy(producto = actual.nombre, iconoClave = actual.iconoClave) }
            }
            val guardado = pedido.copy(orderId = orderRef.id, numeroPedido = numeroPedido, items = items)
            transaccion.update(vendorRef, mapOf("contadorPedidos" to numeroPedido, "ultimoPedidoId" to orderRef.id))
            transaccion.set(orderRef, guardado.aMapaFirestore())
            guardado
        }.await()
    }

    fun escucharPedidosCliente(clientId: String): Flow<List<Order>> = escucharPedidos("clientId", clientId)

    fun escucharPedidosVendedor(vendorId: String): Flow<List<Order>> = escucharPedidos("vendorId", vendorId)

    fun escucharPedido(orderId: String): Flow<Order?> = callbackFlow {
        val registro = firestore.collection(ORDERS).document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.aOrder())
                }
            }
        awaitClose { registro.remove() }
    }

    suspend fun actualizarEstadoPedido(orderId: String, estado: String) {
        val referencia = firestore.collection(ORDERS).document(orderId)
        firestore.runTransaction<Unit> { transaccion ->
            val actual = transaccion.get(referencia).aOrder()
            verificarCondicion(actual != null) { "No se encontró el pedido." }
            if (actual?.estado == estado) return@runTransaction
            verificarCondicion(when (estado) {
                EstadoPedido.RECHAZADO -> actual?.estado == EstadoPedido.PENDIENTE
                EstadoPedido.ENTREGADO -> actual?.estado == EstadoPedido.ACEPTADO
                else -> true
            }) { "El pedido ya cambió de estado. Vuelve a abrirlo." }
            transaccion.update(referencia, "estado", estado)
        }.await()
    }

    suspend fun aceptarPedido(orderId: String) {
        val orderRef = firestore.collection(ORDERS).document(orderId)
        firestore.runTransaction<Unit> { transaccion ->
            val pedido = transaccion.get(orderRef).aOrder() ?: throw FirebaseFirestoreException(
                "No se encontró el pedido.",
                FirebaseFirestoreException.Code.NOT_FOUND,
            )
            if (pedido.estado == EstadoPedido.ACEPTADO) return@runTransaction
            verificarCondicion(pedido.estado == EstadoPedido.PENDIENTE) {
                "Solo se pueden aceptar pedidos pendientes."
            }

            val productos = productos(pedido.vendorId)
            val cambios = pedido.items.groupBy { it.productId }.map { (productId, items) ->
                val referencia = productos.document(productId)
                val producto = transaccion.get(referencia).aProduct() ?: throw FirebaseFirestoreException(
                    "El producto ${items.first().producto} ya no está disponible.",
                    FirebaseFirestoreException.Code.NOT_FOUND,
                )
                val cantidad = items.sumOf { it.cantidad.toLong() }
                verificarCondicion(producto.activo) { "El producto ${producto.nombre} no está activo." }
                verificarCondicion(producto.existencias >= cantidad) {
                    "No hay existencias suficientes de ${producto.nombre}."
                }
                referencia to (producto.existencias - cantidad)
            }
            for ((referencia, existencias) in cambios) {
                val estado = if (existencias <= 0) EstadoProducto.AGOTADO else EstadoProducto.EN_EXISTENCIA
                transaccion.update(referencia, mapOf("existencias" to existencias, "estado" to estado))
            }
            transaccion.update(orderRef, "estado", EstadoPedido.ACEPTADO)
        }.await()
    }

    suspend fun esperarEscriturasPendientes() {
        firestore.waitForPendingWrites().await()
    }

    suspend fun confirmarEnvioPedido(orderId: String) {
        firestore.collection(ORDERS).document(orderId)
            .update("estadoEnvio", EstadoEnvio.CONFIRMADO).await()
    }

    private fun escucharPedidos(campo: String, valor: String): Flow<List<Order>> =
        firestore.collection(ORDERS).whereEqualTo(campo, valor)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .observar { snapshot -> snapshot.documents.mapNotNull { it.aOrder() } }

    private fun vendedor(vendorId: String) = firestore.collection(VENDORS).document(vendorId)

    private fun productos(vendorId: String) = vendedor(vendorId).collection(PRODUCTS)

    private fun productosActivos(vendorId: String) = productos(vendorId).whereEqualTo("activo", true)

    private fun catalogoCliente(vendorId: String) = productosActivos(vendorId)
        .whereEqualTo("estado", EstadoProducto.EN_EXISTENCIA)
        .whereGreaterThan("existencias", 0)

    private fun QuerySnapshot.aProductos(): List<Product> = documents.mapNotNull { it.aProduct() }

    private inline fun verificarCondicion(cumplida: Boolean, mensaje: () -> String) {
        if (!cumplida) {
            throw FirebaseFirestoreException(mensaje(), FirebaseFirestoreException.Code.FAILED_PRECONDITION)
        }
    }

    private fun <T> Query.observar(transformar: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
        val registro = addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(transformar(snapshot))
            }
        }
        awaitClose { registro.remove() }
    }

    private companion object {
        const val USERS = "users"
        const val VENDORS = "vendors"
        const val PRODUCTS = "products"
        const val ORDERS = "orders"
        const val USERNAMES = "usernames"
    }
}
