package com.ambulantitos.feature.vendedor

import androidx.lifecycle.SavedStateHandle
import com.ambulantitos.core.data.ResultadoAmbulantitos
import com.ambulantitos.core.domain.EstadoProducto
import com.ambulantitos.core.domain.ImagenProducto
import com.ambulantitos.core.domain.Product
import com.ambulantitos.core.domain.RolUsuario
import com.ambulantitos.core.domain.User
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FormularioProductoVendedorViewModelTest {
    private val foto = ImagenProducto("https://res.cloudinary.com/prueba/image/upload/foto.jpg", "foto")
    private val vendedor = User(userId = "vendedor", role = RolUsuario.VENDEDOR, vendorId = "negocio")

    @Before fun preparar() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun terminar() { Dispatchers.resetMain() }

    private fun modelo(
        datos: SavedStateHandle = SavedStateHandle(mapOf("imagen_producto" to "content://fotos/uno")),
        perfil: User = vendedor,
        guardar: suspend (String, Product) -> ResultadoAmbulantitos<Product> = { _, p -> ResultadoAmbulantitos.Exito(p) }
    ) = FormularioProductoVendedorViewModel(datos, { ResultadoAmbulantitos.Exito(perfil) }, guardar)

    private fun registrar(
        modelo: FormularioProductoVendedorViewModel,
        nombre: String = "Esquite",
        precio: String = "30,50",
        existencias: String = "12",
        subir: suspend (String) -> ResultadoAmbulantitos<ImagenProducto> = { ResultadoAmbulantitos.Exito(foto) }
    ) = modelo.guardarProducto(nombre, "pieza", precio, existencias, "Con maíz", "", subir)

    @Test fun datosInvalidosNoSubenNiGuardan() = runTest {
        var subidas = 0
        var escrituras = 0
        for ((nombre, precio, cantidad) in listOf(
            Triple("", "30", "12"), Triple("Esquite", "NaN", "12"),
            Triple("Esquite", "Infinity", "12"), Triple("Esquite", "0", "12"),
            Triple("Esquite", "30", "-1"), Triple("Esquite", "30", "1.5")
        )) {
            val modelo = modelo(guardar = { _, p -> escrituras++; ResultadoAmbulantitos.Exito(p) })
            registrar(modelo, nombre, precio, cantidad) { subidas++; ResultadoAmbulantitos.Exito(foto) }
            advanceUntilIdle()
            assertTrue(modelo.estadoGuardado.value is EstadoGuardadoProductoVendedor.Error)
        }
        assertEquals(0, subidas)
        assertEquals(0, escrituras)
    }

    @Test fun sinFotografiaNoEnviaProducto() = runTest {
        var escrituras = 0
        val modelo = modelo(datos = SavedStateHandle(), guardar = { _, p -> escrituras++; ResultadoAmbulantitos.Exito(p) })
        registrar(modelo)
        advanceUntilIdle()
        assertTrue(modelo.estadoGuardado.value is EstadoGuardadoProductoVendedor.Error)
        assertEquals(0, escrituras)
    }

    @Test fun cuentaClienteNoPuedeSubirNiRegistrar() = runTest {
        var subidas = 0
        var escrituras = 0
        val modelo = modelo(perfil = vendedor.copy(role = RolUsuario.CLIENTE), guardar = { _, p ->
            escrituras++
            ResultadoAmbulantitos.Exito(p)
        })
        registrar(modelo) { subidas++; ResultadoAmbulantitos.Exito(foto) }
        advanceUntilIdle()
        assertTrue(modelo.estadoGuardado.value is EstadoGuardadoProductoVendedor.Error)
        assertEquals(0, subidas)
        assertEquals(0, escrituras)
    }

    @Test fun registraFotoNegocioYEstadoSinExistencias() = runTest {
        var negocioGuardado = ""
        val modelo = modelo(guardar = { negocio, producto ->
            negocioGuardado = negocio
            ResultadoAmbulantitos.Exito(producto)
        })
        modelo.agregarIngrediente(" Maíz ")
        registrar(modelo, existencias = "0")
        advanceUntilIdle()
        val producto = (modelo.estadoGuardado.value as EstadoGuardadoProductoVendedor.Completado).producto
        assertEquals("negocio", negocioGuardado)
        assertEquals(30.50, producto.precio, 0.0001)
        assertEquals(EstadoProducto.AGOTADO, producto.estado)
        assertEquals(listOf("Maíz"), producto.ingredientes)
        assertEquals(foto.url, producto.imagenUrl)
        assertEquals(foto.idPublico, producto.imagenIdPublico)
        assertTrue(producto.productId.isNotBlank())
        assertNull(producto.marca)
    }

    @Test fun reintentoTrasFalloFirestoreReutilizaFotoEIdentificador() = runTest {
        var subidas = 0
        val intentos = mutableListOf<Product>()
        val datos = SavedStateHandle(mapOf("imagen_producto" to "content://fotos/uno"))
        val guardar: suspend (String, Product) -> ResultadoAmbulantitos<Product> = { _, producto ->
            intentos.add(producto)
            if (intentos.size == 1) ResultadoAmbulantitos.Error("Sin conexión")
            else ResultadoAmbulantitos.Exito(producto)
        }
        val primero = modelo(datos = datos, guardar = guardar)
        registrar(primero) { subidas++; ResultadoAmbulantitos.Exito(foto) }
        advanceUntilIdle()
        assertTrue(primero.estadoGuardado.value is EstadoGuardadoProductoVendedor.Error)
        // El mismo SavedStateHandle simula recuperar el formulario con sus datos conservados.
        val segundo = modelo(datos = datos, guardar = guardar)
        registrar(segundo) { subidas++; ResultadoAmbulantitos.Exito(foto) }
        advanceUntilIdle()
        assertEquals(1, subidas)
        assertEquals(2, intentos.size)
        assertEquals(intentos[0].productId, intentos[1].productId)
        assertTrue(segundo.estadoGuardado.value is EstadoGuardadoProductoVendedor.Completado)
    }

    @Test fun pulsacionesRepetidasNoDuplicanRegistroNiCambianDatosEnCurso() = runTest {
        var subidas = 0
        var escrituras = 0
        val continuar = CompletableDeferred<Unit>()
        val modelo = modelo(guardar = { _, producto -> escrituras++; ResultadoAmbulantitos.Exito(producto) })
        val subir: suspend (String) -> ResultadoAmbulantitos<ImagenProducto> = {
            subidas++
            continuar.await()
            ResultadoAmbulantitos.Exito(foto)
        }
        registrar(modelo, subir = subir)
        runCurrent()
        assertTrue(modelo.guardando)
        registrar(modelo, subir = subir)
        modelo.seleccionarEstado(EstadoProducto.AGOTADO)
        modelo.seleccionarImagen("content://fotos/otra")
        assertEquals(EstadoProducto.EN_EXISTENCIA, modelo.estadoProducto.value)
        assertEquals("content://fotos/uno", modelo.imagenSeleccionada.value)
        continuar.complete(Unit)
        advanceUntilIdle()
        registrar(modelo, subir = subir)
        advanceUntilIdle()
        assertEquals(1, subidas)
        assertEquals(1, escrituras)
    }

    @Test fun falloDeImagenImpideEscrituraYPermiteReintentar() = runTest {
        var escrituras = 0
        val modelo = modelo(guardar = { _, producto -> escrituras++; ResultadoAmbulantitos.Exito(producto) })
        registrar(modelo) { ResultadoAmbulantitos.Error("La imagen no puede superar los 5 MB.") }
        advanceUntilIdle()
        assertEquals(0, escrituras)
        assertTrue(modelo.estadoGuardado.value is EstadoGuardadoProductoVendedor.Error)
        registrar(modelo)
        advanceUntilIdle()
        assertEquals(1, escrituras)
        assertTrue(modelo.estadoGuardado.value is EstadoGuardadoProductoVendedor.Completado)
    }
}