package com.ambulantitos.feature.vendedor

import androidx.lifecycle.SavedStateHandle
import com.ambulantitos.core.data.ResultadoAmbulantitos
import com.ambulantitos.core.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetalleProductoVendedorViewModelTest {
    private val producto = Product(productId = "p", nombre = "Esquite", precio = 30.0, existencias = 5)
    private val usuario = User(userId = "u", role = RolUsuario.VENDEDOR, vendorId = "negocio")
    @Before fun preparar() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun terminar() { Dispatchers.resetMain() }
    private fun modelo(flujo: MutableStateFlow<Product?>,
        perfil: User = usuario,
        eliminar: suspend (String, String) -> ResultadoAmbulantitos<Unit> = { _, _ -> ResultadoAmbulantitos.Exito(Unit) },
        agotar: suspend (String, String) -> ResultadoAmbulantitos<Unit> = { _, _ -> ResultadoAmbulantitos.Exito(Unit) }
    ) = DetalleProductoVendedorViewModel(
        SavedStateHandle(mapOf(DetalleProductoVendedorActivity.EXTRA_PRODUCTO_ID to "p")),
        { ResultadoAmbulantitos.Exito(perfil) },
        { negocio, id -> assertEquals("negocio", negocio); assertEquals("p", id); flujo }, eliminar, agotar)

    @Test fun muestraActualizacionesDelProducto() = runTest {
        val flujo = MutableStateFlow<Product?>(producto)
        val modelo = modelo(flujo)
        runCurrent()
        assertEquals(producto, (modelo.estado.value as EstadoDetalleProductoVendedor.Disponible).producto)
        flujo.value = producto.copy(precio = 40.0)
        runCurrent()
        assertEquals(40.0, (modelo.estado.value as EstadoDetalleProductoVendedor.Disponible).producto.precio, 0.001)
    }

    @Test fun eliminarNoSeDuplicaYTerminaEnEliminado() = runTest {
        val flujo = MutableStateFlow<Product?>(producto)
        val continuar = CompletableDeferred<Unit>()
        var llamadas = 0
        val modelo = modelo(flujo, eliminar = { negocio, id ->
            assertEquals("negocio", negocio); assertEquals("p", id)
            llamadas++; continuar.await(); ResultadoAmbulantitos.Exito(Unit)
        })
        runCurrent()
        modelo.eliminarProducto()
        runCurrent()
        modelo.eliminarProducto()
        runCurrent()
        assertTrue((modelo.estado.value as EstadoDetalleProductoVendedor.Disponible).procesando)
        continuar.complete(Unit)
        runCurrent()
        assertEquals(1, llamadas)
        assertEquals(EstadoDetalleProductoVendedor.Eliminado, modelo.estado.value)
    }

    @Test fun errorDeEliminacionConservaProductoYPermiteReintentar() = runTest {
        val modelo = modelo(MutableStateFlow(producto), eliminar = { _, _ -> ResultadoAmbulantitos.Error("Sin conexión") })
        runCurrent()
        modelo.eliminarProducto()
        runCurrent()
        assertFalse((modelo.estado.value as EstadoDetalleProductoVendedor.Disponible).procesando)
        assertEquals("Sin conexión", modelo.aviso.value)
        modelo.consumirAviso()
        assertNull(modelo.aviso.value)
    }

    @Test fun marcarAgotadoSeReflejaEnElDetalle() = runTest {
        val flujo = MutableStateFlow<Product?>(producto)
        val modelo = modelo(flujo, agotar = { _, _ ->
            flujo.value = producto.copy(existencias = 0, estado = EstadoProducto.AGOTADO)
            ResultadoAmbulantitos.Exito(Unit)
        })
        runCurrent()
        modelo.marcarAgotado()
        runCurrent()
        val actual = (modelo.estado.value as EstadoDetalleProductoVendedor.Disponible).producto
        assertEquals(0, actual.existencias)
        assertEquals(EstadoProducto.AGOTADO, actual.estado)
    }

    @Test fun productoDesactivadoDesapareceDelDetalle() = runTest {
        val flujo = MutableStateFlow<Product?>(producto)
        val modelo = modelo(flujo)
        runCurrent()
        flujo.value = producto.copy(activo = false)
        runCurrent()
        assertEquals(EstadoDetalleProductoVendedor.Eliminado, modelo.estado.value)
    }

    @Test fun cuentaClienteNoPuedeConsultarElDetalleVendedor() = runTest {
        val modelo = modelo(MutableStateFlow(producto), perfil = usuario.copy(role = RolUsuario.CLIENTE))
        runCurrent()
        assertEquals(EstadoDetalleProductoVendedor.SinSesion, modelo.estado.value)
    }
}