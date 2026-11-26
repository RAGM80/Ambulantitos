package com.ambulantitos.core.data

import com.ambulantitos.core.database.firebase.FuenteAutenticacionFirebase
import com.ambulantitos.core.database.firebase.FuenteFirestore
import com.ambulantitos.core.domain.EstadoEnvio
import com.ambulantitos.core.domain.EstadoPedido
import com.ambulantitos.core.domain.FormasPago
import com.ambulantitos.core.domain.IconoProducto
import com.ambulantitos.core.domain.Order
import com.ambulantitos.core.domain.OrderItem
import com.ambulantitos.core.domain.Product
import com.ambulantitos.core.domain.RolUsuario
import com.ambulantitos.core.domain.User
import com.ambulantitos.core.domain.Vendor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RepositorioFirestore(
    private val auth: FuenteAutenticacionFirebase = FuenteAutenticacionFirebase(),
    private val baseDeDatos: FuenteFirestore = FuenteFirestore(),
) {
    fun uidActual(): String? = auth.uidActual()

    suspend fun obtenerUsuarioActual(): ResultadoAmbulantitos<User> {
        val uid = uidActual() ?: return ResultadoAmbulantitos.Error("No hay una sesión activa.")
        return obtenerUsuario(uid)
    }

    suspend fun obtenerUsuario(userId: String): ResultadoAmbulantitos<User> = ejecutar {
        baseDeDatos.obtenerUsuario(userId) ?: error("No se encontró el perfil del usuario.")
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
        val uid = auth.iniciarSesion(correoDesdeUsuario(usuario), contrasena)
        val perfil = baseDeDatos.obtenerUsuario(uid) ?: error("No se encontró el perfil en Firestore.")
        if (rolEsperado != null && perfil.role != rolEsperado) {
            auth.cerrarSesion()
            error("Esta cuenta no corresponde a este tipo de usuario.")
        }
        perfil
    }

    fun cerrarSesion() = auth.cerrarSesion()

    suspend fun guardarVendedor(vendedor: Vendor): ResultadoAmbulantitos<Vendor> = ejecutar {
        val normalizado = vendedor.copy(formaPagoPorDefecto = FormasPago.EFECTIVO_AL_ENTREGAR)
        baseDeDatos.guardarVendedor(normalizado)
        normalizado
    }

    suspend fun obtenerVendedor(vendorId: String): ResultadoAmbulantitos<Vendor> = ejecutar {
        baseDeDatos.obtenerVendedor(vendorId) ?: error("No se encontró el negocio.")
    }

    fun escucharProductosVendedor(vendorId: String): Flow<List<Product>> =
        baseDeDatos.escucharProductosVendedor(vendorId).map { it.filter(Product::activo) }

    fun escucharCatalogoCliente(vendorId: String): Flow<List<Product>> =
        baseDeDatos.escucharCatalogoCliente(vendorId).map { it.filter(Product::visibleEnCatalogoCliente) }

    suspend fun listarProductosVendedor(vendorId: String): ResultadoAmbulantitos<List<Product>> = ejecutar {
        baseDeDatos.listarProductosVendedor(vendorId).filter(Product::activo)
    }

    suspend fun listarCatalogoCliente(vendorId: String): ResultadoAmbulantitos<List<Product>> = ejecutar {
        baseDeDatos.listarCatalogoCliente(vendorId).filter(Product::visibleEnCatalogoCliente)
    }

    suspend fun obtenerProducto(vendorId: String, productId: String): ResultadoAmbulantitos<Product> = ejecutar {
        require(vendorId.isNotBlank() && productId.isNotBlank()) { "Falta identificar el producto." }
        val producto = baseDeDatos.obtenerProducto(vendorId, productId)
            ?: error("No se encontró el producto.")
        check(producto.activo) { "El producto ya fue eliminado del catálogo." }
        producto
    }

    fun escucharProducto(vendorId: String, productId: String): Flow<Product?> =
        baseDeDatos.escucharProducto(vendorId, productId)

    suspend fun crearProducto(vendorId: String, producto: Product): ResultadoAmbulantitos<Product> = ejecutar {
        val nuevo = normalizarProducto(producto).copy(activo = true)
        baseDeDatos.crearProducto(vendorId, nuevo)
    }

    suspend fun editarProducto(
        vendorId: String,
        producto: Product,
        existenciasEsperadas: Int? = null
    ): ResultadoAmbulantitos<Product> = ejecutar {
        require(producto.productId.isNotBlank()) { "Falta el identificador del producto." }
        baseDeDatos.editarProducto(vendorId, normalizarProducto(producto), existenciasEsperadas)
    }

    suspend fun marcarProductoAgotado(vendorId: String, productId: String): ResultadoAmbulantitos<Unit> =
        ejecutar { baseDeDatos.marcarProductoAgotado(vendorId, productId) }

    suspend fun desactivarProducto(vendorId: String, productId: String): ResultadoAmbulantitos<Unit> =
        ejecutar { baseDeDatos.desactivarProducto(vendorId, productId) }

    suspend fun crearPedido(
        clientId: String,
        clienteNombre: String,
        vendorId: String,
        vendedorNombre: String,
        items: List<OrderItem>,
        modoLocalP2P: Boolean = false,
    ): ResultadoAmbulantitos<Order> = ejecutar {
        require(items.isNotEmpty()) { "El pedido no tiene productos." }
        baseDeDatos.crearPedido(
            Order(
                clientId = clientId,
                clienteNombre = clienteNombre,
                vendorId = vendorId,
                vendedorNombre = vendedorNombre,
                items = items,
                total = items.sumOf { it.subtotal },
                estadoEnvio = EstadoEnvio.CONFIRMADO,
                modoLocalP2P = modoLocalP2P,
                horaCreacion = horaActual(),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    fun escucharPedidosCliente(clientId: String): Flow<List<Order>> = baseDeDatos.escucharPedidosCliente(clientId)

    fun escucharPedidosVendedor(vendorId: String): Flow<List<Order>> = baseDeDatos.escucharPedidosVendedor(vendorId)

    fun escucharPedido(orderId: String): Flow<Order?> = baseDeDatos.escucharPedido(orderId)

    suspend fun actualizarEstadoPedido(
        orderId: String,
        nuevoEstado: String,
    ): ResultadoAmbulantitos<Unit> = when (nuevoEstado) {
        EstadoPedido.ACEPTADO -> aceptarPedido(orderId)
        EstadoPedido.RECHAZADO, EstadoPedido.ENTREGADO, EstadoPedido.PENDIENTE ->
            ejecutar { baseDeDatos.actualizarEstadoPedido(orderId, nuevoEstado) }
        else -> ResultadoAmbulantitos.Error("Estado de pedido no válido.")
    }

    suspend fun aceptarPedido(orderId: String): ResultadoAmbulantitos<Unit> =
        ejecutar { baseDeDatos.aceptarPedido(orderId) }

    suspend fun confirmarEnvioPedido(orderId: String): ResultadoAmbulantitos<Unit> = ejecutar {
        check(confirmarEscrituraPendiente()) {
            "El envío sigue sin confirmar. Revisa la conexión e inténtalo de nuevo."
        }
        baseDeDatos.confirmarEnvioPedido(orderId)
    }

    private suspend fun registrarUsuario(
        perfil: User,
        contrasena: String,
        descripcion: String = "",
        categoriaPrincipal: String = "",
    ): ResultadoAmbulantitos<User> {
        val usuario = normalizarUsuario(perfil.usuario)
        if (usuario.length < 3) {
            return ResultadoAmbulantitos.Error("El usuario debe tener al menos 3 caracteres.")
        }
        if (contrasena.length < 6) {
            return ResultadoAmbulantitos.Error("La contraseña debe tener al menos 6 caracteres.")
        }
        return ejecutar {
            val cuenta = auth.crearCuenta(correoDesdeUsuario(usuario), contrasena)
            val vendorId = cuenta.uid.takeIf { perfil.role == RolUsuario.VENDEDOR }
            val perfilGuardado = perfil.copy(userId = cuenta.uid, vendorId = vendorId, usuario = usuario)
            val vendedor = vendorId?.let {
                Vendor(
                    vendorId = it,
                    ownerUserId = cuenta.uid,
                    nombreNegocio = perfil.nombreNegocio.orEmpty(),
                    descripcion = descripcion,
                    categoriaPrincipal = categoriaPrincipal,
                )
            }
            try {
                baseDeDatos.guardarRegistro(perfilGuardado, vendedor)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                try {
                    cuenta.eliminar()
                } catch (cancelacion: CancellationException) {
                    throw cancelacion
                } catch (errorAlEliminar: Exception) {
                    error.addSuppressed(errorAlEliminar)
                }
                throw error
            }
            perfilGuardado
        }
    }

    private fun normalizarProducto(producto: Product): Product = producto.copy(
        iconoClave = IconoProducto.normalizar(producto.iconoClave),
        ingredientes = producto.ingredientes.take(6),
    )

    private suspend fun confirmarEscrituraPendiente(timeoutMs: Long = 8_000L): Boolean {
        return try {
            withTimeoutOrNull(timeoutMs) { baseDeDatos.esperarEscriturasPendientes() } != null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun <T> ejecutar(bloque: suspend () -> T): ResultadoAmbulantitos<T> {
        return try {
            ResultadoAmbulantitos.Exito(bloque())
        } catch (error: CancellationException) {
            throw error
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
        private val instancia by lazy { RepositorioFirestore() }

        fun obtener(): RepositorioFirestore = instancia

        fun correoDesdeUsuario(usuario: String): String =
            "${normalizarUsuario(usuario)}@usuarios.ambulantitos.app"

        fun normalizarUsuario(usuario: String): String = usuario.trim().lowercase(Locale.ROOT)

        private fun horaActual(): String {
            val formato = SimpleDateFormat("HH:mm", Locale("es", "MX"))
            formato.timeZone = TimeZone.getDefault()
            return formato.format(Date())
        }
    }
}
