package com.ambulantitos.feature.vendedor

import androidx.lifecycle.SavedStateHandle
import com.ambulantitos.core.data.ResultadoAmbulantitos
import com.ambulantitos.core.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EdicionProductoVendedorViewModelTest {
    private val usuario = User(userId = "u", role = RolUsuario.VENDEDOR, vendorId = "negocio")
    private val original = Product(productId = "producto", nombre = "Elote", iconoClave = "elote",
        precio = 20.0, existencias = 8, ingredientes = listOf("Maíz"),
        imagenUrl = "https://res.cloudinary.com/demo/image/upload/foto.jpg", imagenIdPublico = "foto")
    @Before fun preparar() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun terminar() { Dispatchers.resetMain() }

    private fun modelo(datos: SavedStateHandle = SavedStateHandle(mapOf(DetalleProductoVendedorActivity.EXTRA_PRODUCTO_ID to "producto")),
        consulta: ResultadoAmbulantitos<Product> = ResultadoAmbulantitos.Exito(original),
        actualizar: suspend (String, Product, Int) -> ResultadoAmbulantitos<Product>
    ) = FormularioProductoVendedorViewModel(datos,
        { ResultadoAmbulantitos.Exito(usuario) },
        { _, _ -> error("Una edición no debe crear otro producto") },
        { _, _ -> consulta }, actualizar)

    @Test fun cargaDatosYEditaSinVolverASubirLaFoto() = runTest {
        var actualizado: Product? = null
        var esperado = -1
        val modelo = modelo { negocio, producto, existencias ->
            assertEquals("negocio", negocio)
            esperado = existencias
            actualizado = producto
            ResultadoAmbulantitos.Exito(producto)
        }
        advanceUntilIdle()
        assertTrue(modelo.esEdicion)
        assertEquals(original, modelo.productoParaEditar.value)
        assertEquals(listOf("Maíz"), modelo.ingredientes.value)
        assertEquals(original.imagenUrl, modelo.imagenSeleccionada.value)
        modelo.guardarProducto("Elote grande", "pieza", "25", "10", "Con queso", "") {
            error("La foto original debe reutilizarse")
        }
        advanceUntilIdle()
        assertEquals("producto", actualizado?.productId)
        assertEquals("elote", actualizado?.iconoClave)
        assertEquals("Elote grande", actualizado?.nombre)
        assertEquals(original.imagenUrl, actualizado?.imagenUrl)
        assertEquals(8, esperado)
        assertTrue(modelo.estadoGuardado.value is EstadoGuardadoProductoVendedor.Completado)
    }

    @Test fun cambiarFotografiaActualizaSuEnlace() = runTest {
        var subidas = 0
        var actualizado: Product? = null
        val modelo = modelo { _, producto, _ -> actualizado = producto; ResultadoAmbulantitos.Exito(producto) }
        advanceUntilIdle()
        modelo.seleccionarImagen("content://fotos/nueva")
        modelo.guardarProducto("Elote", "pieza", "20", "8", "", "") { direccion ->
            assertEquals("content://fotos/nueva", direccion)
            subidas++
            ResultadoAmbulantitos.Exito(ImagenProducto("https://res.cloudinary.com/demo/image/upload/nueva.jpg", "nueva"))
        }
        advanceUntilIdle()
        assertEquals(1, subidas)
        assertEquals("nueva", actualizado?.imagenIdPublico)
        assertEquals("producto", actualizado?.productId)
    }

    @Test fun reponerPreseleccionaDisponibleYConservaLaCantidadEsperada() = runTest {
        val datos = SavedStateHandle(mapOf(DetalleProductoVendedorActivity.EXTRA_PRODUCTO_ID to "producto",
            DetalleProductoVendedorActivity.EXTRA_REPONER to true))
        val agotado = original.copy(existencias = 0, estado = EstadoProducto.AGOTADO)
        var actualizado: Product? = null
        val modelo = modelo(datos, ResultadoAmbulantitos.Exito(agotado)) { _, producto, esperado ->
            assertEquals(0, esperado)
            actualizado = producto
            ResultadoAmbulantitos.Exito(producto)
        }
        advanceUntilIdle()
        assertEquals(EstadoProducto.EN_EXISTENCIA, modelo.estadoProducto.value)
        modelo.guardarProducto("Elote", "pieza", "20", "12", "", "") { error("No subir de nuevo") }
        advanceUntilIdle()
        assertEquals(12, actualizado?.existencias)
        assertEquals(EstadoProducto.EN_EXISTENCIA, actualizado?.estado)
    }

    @Test fun falloAlCargarNoPermiteGuardar() = runTest {
        var escrituras = 0
        val modelo = modelo(consulta = ResultadoAmbulantitos.Error("Producto eliminado")) { _, producto, _ ->
            escrituras++
            ResultadoAmbulantitos.Exito(producto)
        }
        advanceUntilIdle()
        modelo.guardarProducto("Elote", "pieza", "20", "8", "", "") { error("No subir") }
        advanceUntilIdle()
        assertEquals(0, escrituras)
        assertTrue(modelo.estadoGuardado.value is EstadoGuardadoProductoVendedor.Error)
    }

    @Test fun conflictoDeExistenciasMantieneElFormularioConError() = runTest {
        val modelo = modelo { _, _, esperado ->
            assertEquals(original.existencias, esperado)
            ResultadoAmbulantitos.Error("Las existencias cambiaron mientras editabas.")
        }
        advanceUntilIdle()
        modelo.guardarProducto("Elote", "pieza", "20", "10", "", "") { error("No subir") }
        advanceUntilIdle()
        assertTrue(modelo.estadoGuardado.value is EstadoGuardadoProductoVendedor.Error)
        assertFalse(modelo.guardando)
    }
}