package com.ambulantitos.core.data

import com.ambulantitos.core.data.firebase.FirebaseAmbulantitos
import com.ambulantitos.core.data.firebase.aMapaFirestore
import com.ambulantitos.core.data.firebase.aOrder
import com.ambulantitos.core.data.firebase.aProduct
import com.ambulantitos.core.data.firebase.aUser
import com.ambulantitos.core.data.firebase.aVendor
import com.ambulantitos.core.domain.ColeccionesFirestore
import com.ambulantitos.core.domain.EstadoEnvio
import com.ambulantitos.core.domain.EstadoPedido
import com.ambulantitos.core.domain.EstadoProducto
import com.ambulantitos.core.domain.FormasPago
import com.ambulantitos.core.domain.IconoProducto
import com.ambulantitos.core.domain.Order
import com.ambulantitos.core.domain.OrderItem
import com.ambulantitos.core.domain.Product
import com.ambulantitos.core.domain.RolUsuario
import com.ambulantitos.core.domain.User
import com.ambulantitos.core.domain.Vendor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RepositorioFirestore(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    init {
        FirebaseAmbulantitos.activarCachePersistente()
    }

    fun uidActual(): String? = auth.currentUser?.uid

    suspend fun obtenerUsuarioActual(): ResultadoAmbulantitos<User> {
        val uid = uidActual() ?: return ResultadoAmbulantitos.Error("No hay una sesión activa.")
        return obtenerUsuario(uid)
    }

    suspend fun obtenerUsuario(userId: String): ResultadoAmbulantitos<User> = ejecutar {
        val snapshot = firestore.collection(ColeccionesFirestore.USERS).document(userId).get().await()
        snapshot.aUser() ?: error("No se encontró el perfil del usuario.")
    }

    suspend fun registrarCliente(
        nombres: String,
        apellidoPaterno: String,
        apellidoMaterno: String,
        fechaNacimiento: String,
        numeroCelular: String,
        usuario: String,
        contrasena: String,
    ): ResultadoAmbulantitos<User> {
        val perfil = User(
            role = RolUsuario.CLIENTE,
            nombres = nombres.trim(),
            apellidoPaterno = apellidoPaterno.trim(),
            apellidoMaterno = apellidoMaterno.trim(),
            fechaNacimiento = fechaNacimiento.trim(),
            usuario = usuario.trim(),
            numeroCelular = numeroCelular.trim(),
        )
        return registrarUsuario(perfil, contrasena)
    }

    suspend fun registrarVendedor(
        nombres: String,
        apellidoPaterno: String,
        apellidoMaterno: String,
        fechaNacimiento: String,
        nombreNegocio: String,
        usuario: String,
        contrasena: String,
        descripcion: String = "",
        categoriaPrincipal: String = "",
    ): ResultadoAmbulantitos<User> {
        val perfil = User(
            role = RolUsuario.VENDEDOR,
            nombres = nombres.trim(),
            apellidoPaterno = apellidoPaterno.trim(),
            apellidoMaterno = apellidoMaterno.trim(),
            fechaNacimiento = fechaNacimiento.trim(),
            usuario = usuario.trim(),
            nombreNegocio = nombreNegocio.trim(),
        )
        return registrarUsuario(perfil, contrasena, descripcion, categoriaPrincipal)
    }

    suspend fun iniciarSesion(
        usuario: String,
        contrasena: String,
        rolEsperado: String? = null,
    ): ResultadoAmbulantitos<User> = ejecutar {
        val correo = correoDesdeUsuario(usuario)
        auth.signInWithEmailAndPassword(correo, contrasena).await()
        val uid = uidActual() ?: error("No se pudo iniciar sesión.")
        val perfil = firestore.collection(ColeccionesFirestore.USERS).document(uid).get().await().aUser()
            ?: error("No se encontró el perfil en Firestore.")
        if (rolEsperado != null && perfil.role != rolEsperado) {
            auth.signOut()
            error("Esta cuenta no corresponde a este tipo de usuario.")
        }
        perfil
    }

    fun cerrarSesion() {
        auth.signOut()
    }

    suspend fun guardarVendedor(vendedor: Vendor): ResultadoAmbulantitos<Vendor> = ejecutar {
        val normalizado = vendedor.copy(formaPagoPorDefecto = FormasPago.EFECTIVO_AL_ENTREGAR)
        firestore.collection(ColeccionesFirestore.VENDORS)
            .document(normalizado.vendorId)
            .set(normalizado.aMapaFirestore(), SetOptions.merge())
            .await()
        normalizado
    }

    suspend fun obtenerVendedor(vendorId: String): ResultadoAmbulantitos<Vendor> = ejecutar {
        val snapshot = firestore.collection(ColeccionesFirestore.VENDORS).document(vendorId).get().await()
        snapshot.aVendor() ?: error("No se encontró el negocio.")
    }

    fun escucharProductosVendedor(vendorId: String): Flow<List<Product>> =
        callbackFlow {
            val registro = productos(vendorId)
                .whereEqualTo("activo", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val productos = snapshot?.documents
                        ?.mapNotNull { it.aProduct() }
                        ?.filter { it.activo }
                        .orEmpty()
                    trySend(productos)
                }
            awaitClose { registro.remove() }
        }

    fun escucharCatalogoCliente(vendorId: String): Flow<List<Product>> =
        callbackFlow {
            val registro = productos(vendorId)
                .whereEqualTo("activo", true)
                .whereEqualTo("estado", EstadoProducto.EN_EXISTENCIA)
                .whereGreaterThan("existencias", 0)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val productos = snapshot?.documents
                        ?.mapNotNull { it.aProduct() }
                        ?.filter { it.visibleEnCatalogoCliente }
                        .orEmpty()
                    trySend(productos)
                }
            awaitClose { registro.remove() }
        }

    suspend fun listarProductosVendedor(vendorId: String): ResultadoAmbulantitos<List<Product>> = ejecutar {
        productos(vendorId)
            .whereEqualTo("activo", true)
            .get()
            .await()
            .documents
            .mapNotNull { it.aProduct() }
            .filter { it.activo }
    }

    suspend fun listarCatalogoCliente(vendorId: String): ResultadoAmbulantitos<List<Product>> = ejecutar {
        productos(vendorId)
            .whereEqualTo("activo", true)
            .whereEqualTo("estado", EstadoProducto.EN_EXISTENCIA)
            .whereGreaterThan("existencias", 0)
            .get()
            .await()
            .documents
            .mapNotNull { it.aProduct() }
            .filter { it.visibleEnCatalogoCliente }
    }

    suspend fun crearProducto(vendorId: String, producto: Product): ResultadoAmbulantitos<Product> = ejecutar {
        val referencia = if (producto.productId.isBlank()) {
            productos(vendorId).document()
        } else {
            productos(vendorId).document(producto.productId)
        }
        val guardado = producto.copy(
            productId = referencia.id,
            iconoClave = IconoProducto.normalizar(producto.iconoClave),
            ingredientes = producto.ingredientes.take(6),
            activo = true,
        )
        referencia.set(guardado.aMapaFirestore()).await()
        guardado
    }

    suspend fun editarProducto(vendorId: String, producto: Product): ResultadoAmbulantitos<Product> = ejecutar {
        require(producto.productId.isNotBlank()) { "Falta el identificador del producto." }
        val actualizado = producto.copy(
            iconoClave = IconoProducto.normalizar(producto.iconoClave),
            ingredientes = producto.ingredientes.take(6),
        )
        productos(vendorId).document(producto.productId)
            .set(actualizado.aMapaFirestore(), SetOptions.merge())
            .await()
        actualizado
    }

    suspend fun marcarProductoAgotado(vendorId: String, productId: String): ResultadoAmbulantitos<Unit> =
        ejecutar {
            productos(vendorId).document(productId).update(
                mapOf(
                    "estado" to EstadoProducto.AGOTADO,
                    "existencias" to 0,
                ),
            ).await()
        }

    suspend fun desactivarProducto(vendorId: String, productId: String): ResultadoAmbulantitos<Unit> =
        ejecutar {
            productos(vendorId).document(productId).update("activo", false).await()
        }

    suspend fun crearPedido(
        clientId: String,
        clienteNombre: String,
        vendorId: String,
        vendedorNombre: String,
        items: List<OrderItem>,
        modoLocalP2P: Boolean = false,
        timeoutConfirmacionMs: Long = 8_000L,
    ): ResultadoAmbulantitos<Order> = ejecutar {
        require(items.isNotEmpty()) { "El pedido no tiene productos." }
        val ahora = System.currentTimeMillis()
        val vendorRef = firestore.collection(ColeccionesFirestore.VENDORS).document(vendorId)
        val orderRef = firestore.collection(ColeccionesFirestore.ORDERS).document()
        val numeroPedido = firestore.runTransaction { transaccion ->
            val vendorSnap = transaccion.get(vendorRef)
            val actual = (vendorSnap.getLong("contadorPedidos") ?: 0L).toInt()
            val siguiente = actual + 1
            transaccion.update(vendorRef, "contadorPedidos", siguiente)
            siguiente
        }.await()

        val pedido = Order(
            orderId = orderRef.id,
            numeroPedido = numeroPedido,
            clientId = clientId,
            clienteNombre = clienteNombre,
            vendorId = vendorId,
            vendedorNombre = vendedorNombre,
            items = items,
            total = items.sumOf { it.subtotal },
            formaPago = FormasPago.EFECTIVO_AL_ENTREGAR,
            estado = EstadoPedido.PENDIENTE,
            estadoEnvio = EstadoEnvio.SIN_CONFIRMAR,
            modoLocalP2P = modoLocalP2P,
            horaCreacion = horaActual(),
            createdAt = ahora,
        )
        orderRef.set(pedido.aMapaFirestore()).await()
        val confirmado = confirmarEscrituraPendiente(timeoutConfirmacionMs)
        if (confirmado) {
            orderRef.update("estadoEnvio", EstadoEnvio.CONFIRMADO).await()
            pedido.copy(estadoEnvio = EstadoEnvio.CONFIRMADO)
        } else {
            pedido
        }
    }

    fun escucharPedidosCliente(clientId: String): Flow<List<Order>> =
        escucharPedidos("clientId", clientId)

    fun escucharPedidosVendedor(vendorId: String): Flow<List<Order>> =
        escucharPedidos("vendorId", vendorId)

    fun escucharPedido(orderId: String): Flow<Order?> = callbackFlow {
        val registro: ListenerRegistration = firestore.collection(ColeccionesFirestore.ORDERS)
            .document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.aOrder())
            }
        awaitClose { registro.remove() }
    }

    suspend fun actualizarEstadoPedido(
        orderId: String,
        nuevoEstado: String,
    ): ResultadoAmbulantitos<Unit> {
        return when (nuevoEstado) {
            EstadoPedido.ACEPTADO -> aceptarPedido(orderId)
            EstadoPedido.RECHAZADO,
            EstadoPedido.ENTREGADO,
            EstadoPedido.PENDIENTE,
            -> ejecutar {
                firestore.collection(ColeccionesFirestore.ORDERS)
                    .document(orderId)
                    .update("estado", nuevoEstado)
                    .await()
            }
            else -> ResultadoAmbulantitos.Error("Estado de pedido no válido.")
        }
    }

    suspend fun aceptarPedido(orderId: String): ResultadoAmbulantitos<Unit> = ejecutar {
        val orderRef = firestore.collection(ColeccionesFirestore.ORDERS).document(orderId)
        firestore.runTransaction { transaccion ->
            val orderSnap = transaccion.get(orderRef)
            val pedido = orderSnap.aOrder() ?: throw FirebaseFirestoreException(
                "No se encontró el pedido.",
                FirebaseFirestoreException.Code.NOT_FOUND,
            )
            if (pedido.estado == EstadoPedido.ACEPTADO) {
                return@runTransaction null
            }
            if (pedido.estado != EstadoPedido.PENDIENTE) {
                throw FirebaseFirestoreException(
                    "Solo se pueden aceptar pedidos pendientes.",
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION,
                )
            }
            pedido.items.forEach { item ->
                val productRef = productos(pedido.vendorId).document(item.productId)
                val productSnap = transaccion.get(productRef)
                val producto = productSnap.aProduct() ?: throw FirebaseFirestoreException(
                    "El producto ${item.producto} ya no está disponible.",
                    FirebaseFirestoreException.Code.NOT_FOUND,
                )
                if (!producto.activo) {
                    throw FirebaseFirestoreException(
                        "El producto ${producto.nombre} no está activo.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION,
                    )
                }
                if (producto.existencias < item.cantidad) {
                    throw FirebaseFirestoreException(
                        "No hay existencias suficientes de ${producto.nombre}.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION,
                    )
                }
                val nuevasExistencias = producto.existencias - item.cantidad
                transaccion.update(
                    productRef,
                    mapOf(
                        "existencias" to nuevasExistencias,
                        "estado" to if (nuevasExistencias <= 0) {
                            EstadoProducto.AGOTADO
                        } else {
                            EstadoProducto.EN_EXISTENCIA
                        },
                    ),
                )
            }
            transaccion.update(orderRef, "estado", EstadoPedido.ACEPTADO)
            null
        }.await()
    }

    suspend fun confirmarEnvioPedido(orderId: String): ResultadoAmbulantitos<Unit> = ejecutar {
        val confirmado = confirmarEscrituraPendiente()
        if (!confirmado) {
            error("El envío sigue sin confirmar. Revisa la conexión e inténtalo de nuevo.")
        }
        firestore.collection(ColeccionesFirestore.ORDERS)
            .document(orderId)
            .update("estadoEnvio", EstadoEnvio.CONFIRMADO)
            .await()
    }

    private suspend fun registrarUsuario(
        perfil: User,
        contrasena: String,
        descripcion: String = "",
        categoriaPrincipal: String = "",
    ): ResultadoAmbulantitos<User> {
        val usuarioNormalizado = normalizarUsuario(perfil.usuario)
        if (usuarioNormalizado.length < 3) {
            return ResultadoAmbulantitos.Error("El usuario debe tener al menos 3 caracteres.")
        }
        if (contrasena.length < 6) {
            return ResultadoAmbulantitos.Error("La contraseña debe tener al menos 6 caracteres.")
        }
        return ejecutar {
            val correo = correoDesdeUsuario(perfil.usuario)
            val credencial = auth.createUserWithEmailAndPassword(correo, contrasena).await()
            val uid = credencial.user?.uid ?: error("No se pudo crear la cuenta.")
            val usernameRef = firestore.collection(ColeccionesFirestore.USERNAMES).document(usuarioNormalizado)
            val userRef = firestore.collection(ColeccionesFirestore.USERS).document(uid)
            try {
                val vendorId = if (perfil.role == RolUsuario.VENDEDOR) uid else null
                val perfilGuardado = perfil.copy(userId = uid, vendorId = vendorId, usuario = usuarioNormalizado)
                firestore.runTransaction { transaccion ->
                    val existente = transaccion.get(usernameRef)
                    if (existente.exists()) {
                        throw FirebaseFirestoreException(
                            "Ese nombre de usuario ya está ocupado.",
                            FirebaseFirestoreException.Code.ALREADY_EXISTS,
                        )
                    }
                    transaccion.set(userRef, perfilGuardado.aMapaFirestore())
                    transaccion.set(
                        usernameRef,
                        mapOf(
                            "userId" to uid,
                            "role" to perfil.role,
                            "usuario" to usuarioNormalizado,
                        ),
                    )
                    if (perfil.role == RolUsuario.VENDEDOR && vendorId != null) {
                        val vendedor = Vendor(
                            vendorId = vendorId,
                            ownerUserId = uid,
                            nombreNegocio = perfil.nombreNegocio.orEmpty(),
                            descripcion = descripcion,
                            categoriaPrincipal = categoriaPrincipal,
                            formaPagoPorDefecto = FormasPago.EFECTIVO_AL_ENTREGAR,
                        )
                        transaccion.set(
                            firestore.collection(ColeccionesFirestore.VENDORS).document(vendorId),
                            vendedor.aMapaFirestore(),
                        )
                    }
                }.await()
                perfilGuardado
            } catch (error: Exception) {
                credencial.user?.delete()?.await()
                throw error
            }
        }
    }

    private fun escucharPedidos(campo: String, valor: String): Flow<List<Order>> = callbackFlow {
        val registro = firestore.collection(ColeccionesFirestore.ORDERS)
            .whereEqualTo(campo, valor)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.aOrder() }.orEmpty())
            }
        awaitClose { registro.remove() }
    }

    private fun productos(vendorId: String) =
        firestore.collection(ColeccionesFirestore.VENDORS)
            .document(vendorId)
            .collection(ColeccionesFirestore.PRODUCTS)

    private suspend fun confirmarEscrituraPendiente(timeoutMs: Long = 8_000L): Boolean {
        return try {
            withTimeout(timeoutMs) {
                firestore.waitForPendingWrites().await()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun <T> ejecutar(bloque: suspend () -> T): ResultadoAmbulantitos<T> {
        return try {
            ResultadoAmbulantitos.Exito(bloque())
        } catch (error: Exception) {
            ResultadoAmbulantitos.Error(mensajeAmigable(error), error)
        }
    }

    private fun mensajeAmigable(error: Throwable): String {
        val texto = error.message.orEmpty()
        return when {
            texto.contains("password is invalid", ignoreCase = true) ||
                texto.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ->
                "Usuario o contraseña incorrectos."
            texto.contains("email address is already in use", ignoreCase = true) ->
                "Ese nombre de usuario ya está ocupado."
            texto.contains("network", ignoreCase = true) ->
                "No hay conexión. Inténtalo de nuevo."
            texto.isNotBlank() -> texto
            else -> "Ocurrió un error. Inténtalo de nuevo."
        }
    }

    companion object {
        @Volatile
        private var instancia: RepositorioFirestore? = null

        fun obtener(): RepositorioFirestore {
            return instancia ?: synchronized(this) {
                instancia ?: RepositorioFirestore().also { instancia = it }
            }
        }

        fun correoDesdeUsuario(usuario: String): String {
            return "${normalizarUsuario(usuario)}@usuarios.ambulantitos.app"
        }

        fun normalizarUsuario(usuario: String): String {
            return usuario.trim().lowercase(Locale.ROOT)
        }

        private fun horaActual(): String {
            val formato = SimpleDateFormat("HH:mm", Locale("es", "MX"))
            formato.timeZone = TimeZone.getDefault()
            return formato.format(Date())
        }
    }
}
