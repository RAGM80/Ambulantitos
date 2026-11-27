package com.ambulantitos.feature.cliente

import com.ambulantitos.core.domain.Ubicacion
import kotlin.math.pow
import kotlin.math.roundToInt

enum class FiltroCategoriaCliente {
    TODOS,
    COMIDA,
    BEBIDAS,
}

data class VendedorEnMapa(
    val direccionBle: String,
    val nombreAnuncio: String,
    val rssi: Int = -70,
    val vendorId: String? = null,
    val nombreNegocio: String = "",
    val categoriaPrincipal: String = "",
    val ubicacionReal: Ubicacion? = null,
) {
    val nombreVisible: String
        get() = nombreNegocio.ifBlank {
            nombreAnuncio.ifBlank { "Vendedor ${direccionBle.takeLast(5)}" }
        }

    val distanciaEstimadaMetros: Double
        get() = 10.0.pow((-59.0 - rssi) / 20.0).coerceIn(2.0, 45.0)

    val distanciaRedondeadaMetros: Int
        get() = distanciaEstimadaMetros.roundToInt()

    val tieneUbicacionReal: Boolean
        get() = ubicacionReal != null && (ubicacionReal.lat != 0.0 || ubicacionReal.lng != 0.0)
}

data class EstadoBusquedaCliente(
    val buscandoBle: Boolean = false,
    val identificandoBle: Boolean = false,
    val mensajeEstado: String? = null,
    val textoBusqueda: String = "",
    val filtroCategoria: FiltroCategoriaCliente = FiltroCategoriaCliente.TODOS,
    val ubicacionCliente: Ubicacion? = null,
    val vendedoresDetectados: List<VendedorEnMapa> = emptyList(),
    val vendedorSeleccionado: VendedorEnMapa? = null,
) {
    fun vendedoresVisibles(): List<VendedorEnMapa> {
        val consulta = textoBusqueda.trim().lowercase()
        return vendedoresDetectados.filter { vendedor ->
            val coincideTexto = consulta.isEmpty() ||
                vendedor.nombreVisible.lowercase().contains(consulta) ||
                vendedor.categoriaPrincipal.lowercase().contains(consulta)
            val coincideCategoria = when (filtroCategoria) {
                FiltroCategoriaCliente.TODOS -> true
                FiltroCategoriaCliente.COMIDA -> {
                    val cat = vendedor.categoriaPrincipal.lowercase()
                    cat.isBlank() || cat.contains("comida") || cat.contains("elote") ||
                        cat.contains("esquite") || cat.contains("taco") || cat.contains("antoj")
                }
                FiltroCategoriaCliente.BEBIDAS -> {
                    val cat = vendedor.categoriaPrincipal.lowercase()
                    cat.contains("bebida") || cat.contains("agua") || cat.contains("jugo") ||
                        cat.contains("café") || cat.contains("cafe") || cat.contains("refresco")
                }
            }
            coincideTexto && coincideCategoria
        }
    }
}
