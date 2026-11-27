package com.ambulantitos.feature.cliente

import com.ambulantitos.core.domain.Ubicacion
import com.ambulantitos.core.domain.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BusquedaClienteViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun configurar() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    @Test
    fun registraYActualizaVendedorBleEnElMapa() {
        val vm = BusquedaClienteViewModel(obtenerPerfilVendedor = { null })

        vm.alIniciarBusquedaBle()
        assertTrue(vm.estado.value.buscandoBle)

        vm.alEncontrarVendedorBle("AA:BB:CC:DD:EE:01", "Elotitos", -65)
        vm.alEncontrarVendedorBle("AA:BB:CC:DD:EE:01", "Elotitos", -55)
        vm.alTerminarBusquedaBle(null)

        val estado = vm.estado.value
        assertFalse(estado.buscandoBle)
        assertEquals(1, estado.vendedoresDetectados.size)
        assertEquals(-55, estado.vendedoresDetectados.first().rssi)
        assertEquals("Elotitos", estado.vendedoresDetectados.first().nombreVisible)
    }

    @Test
    fun identificarVendedorEnriquecePerfilYUbicacionDesdeFirestore() = runTest {
        val perfil = Vendor(
            vendorId = "vendor_123",
            nombreNegocio = "Esquites Don Rafa",
            categoriaPrincipal = "Comida",
            ubicacion = Ubicacion(lat = 19.4326, lng = -99.1332),
        )
        val vm = BusquedaClienteViewModel(obtenerPerfilVendedor = { perfil })

        vm.alEncontrarVendedorBle("AA:BB:CC:DD:EE:02", "Esquites", -62)
        vm.alIdentificarVendedor("AA:BB:CC:DD:EE:02", "vendor_123", "Esquites")
        advanceUntilIdle()

        val seleccionado = vm.estado.value.vendedorSeleccionado
        assertEquals("vendor_123", seleccionado?.vendorId)
        assertEquals("Esquites Don Rafa", seleccionado?.nombreVisible)
        assertEquals("Comida", seleccionado?.categoriaPrincipal)
        assertTrue(seleccionado?.tieneUbicacionReal == true)
    }

    @Test
    fun filtraPorTextoYCategoriaCorrectamente() = runTest {
        val perfiles = mapOf(
            "v1" to Vendor(vendorId = "v1", nombreNegocio = "Elotes Rafa", categoriaPrincipal = "Comida"),
            "v2" to Vendor(vendorId = "v2", nombreNegocio = "Aguas Frescas", categoriaPrincipal = "Bebidas"),
        )
        val vm = BusquedaClienteViewModel(obtenerPerfilVendedor = { perfiles[it] })

        vm.alEncontrarVendedorBle("11:11", "Elotes", -60)
        vm.alEncontrarVendedorBle("22:22", "Aguas", -64)
        vm.alIdentificarVendedor("11:11", "v1", "Elotes")
        vm.alIdentificarVendedor("22:22", "v2", "Aguas")
        advanceUntilIdle()

        vm.seleccionarFiltro(FiltroCategoriaCliente.BEBIDAS)
        assertEquals(1, vm.estado.value.vendedoresVisibles().size)
        assertEquals("Aguas Frescas", vm.estado.value.vendedoresVisibles().first().nombreVisible)

        vm.seleccionarFiltro(FiltroCategoriaCliente.TODOS)
        vm.actualizarTextoBusqueda("rafa")
        assertEquals(1, vm.estado.value.vendedoresVisibles().size)
        assertEquals("Elotes Rafa", vm.estado.value.vendedoresVisibles().first().nombreVisible)
    }
}
