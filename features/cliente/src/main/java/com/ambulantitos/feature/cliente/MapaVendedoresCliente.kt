package com.ambulantitos.feature.cliente

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.ambulantitos.core.domain.Ubicacion
import com.ambulantitos.feature.cliente.R as ClienteR
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * Encapsula el mapa gratuito de OpenStreetMap (OSMDroid) para mostrar la ubicación
 * del cliente, el radio de búsqueda BLE y los pines interactivos de vendedores cercanos.
 */
internal class MapaVendedoresCliente(
    private val context: Context,
    contenedor: ViewGroup,
    private val onVendedorTocado: (VendedorEnMapa) -> Unit,
) {
    private val mapView: MapView
    private var centroActual: GeoPoint = PUNTO_DEFECTO
    private var yaCentradoEnUsuario = false
    private val marcadoresVendedores = mutableListOf<Marker>()
    private var marcadorCliente: Marker? = null
    private var circuloAlcanceBle: Polygon? = null

    init {
        val config = Configuration.getInstance()
        config.userAgentValue = context.packageName
        val baseDir = File(context.cacheDir, "osmdroid")
        val tileCache = File(baseDir, "tiles")
        config.osmdroidBasePath = baseDir
        config.osmdroidTileCache = tileCache

        mapView = MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(ZOOM_INICIAL)
            controller.setCenter(centroActual)
        }

        contenedor.removeAllViews()
        contenedor.addView(
            mapView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        dibujarZonaCliente(centroActual)
    }

    fun actualizarUbicacionCliente(ubicacion: Ubicacion) {
        val punto = GeoPoint(ubicacion.lat, ubicacion.lng)
        centroActual = punto
        dibujarZonaCliente(punto)
        if (!yaCentradoEnUsuario) {
            yaCentradoEnUsuario = true
            mapView.controller.animateTo(punto, ZOOM_INICIAL, 500L)
        }
        mapView.invalidate()
    }

    fun actualizarVendedores(
        vendedores: List<VendedorEnMapa>,
        seleccionado: VendedorEnMapa?,
    ) {
        marcadoresVendedores.forEach { mapView.overlays.remove(it) }
        marcadoresVendedores.clear()

        val iconoVendedor = ContextCompat.getDrawable(context, ClienteR.drawable.icono_ubicacion_cliente)

        vendedores.forEach { vendedor ->
            val posicion = calcularPosicionEnMapa(vendedor)
            val marcador = Marker(mapView).apply {
                position = posicion
                title = vendedor.nombreVisible
                snippet = if (vendedor.tieneUbicacionReal) {
                    context.getString(ClienteR.string.cliente_mapa_ubicacion_real)
                } else {
                    context.getString(
                        ClienteR.string.cliente_mapa_distancia_ble,
                        vendedor.distanciaRedondeadaMetros,
                    )
                }
                icon = iconoVendedor
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { marker, _ ->
                    marker.showInfoWindow()
                    onVendedorTocado(vendedor)
                    true
                }
            }
            mapView.overlays.add(marcador)
            marcadoresVendedores.add(marcador)

            if (seleccionado != null && seleccionado.direccionBle == vendedor.direccionBle) {
                marcador.showInfoWindow()
            }
        }

        mapView.invalidate()
    }

    fun centrarEnVendedor(vendedor: VendedorEnMapa) {
        val posicion = calcularPosicionEnMapa(vendedor)
        mapView.controller.animateTo(posicion, ZOOM_SELECCION, 400L)
    }

    fun onResume() {
        mapView.onResume()
    }

    fun onPause() {
        mapView.onPause()
    }

    fun onDestroy() {
        mapView.onDetach()
    }

    private fun dibujarZonaCliente(centro: GeoPoint) {
        circuloAlcanceBle?.let { mapView.overlays.remove(it) }
        marcadorCliente?.let { mapView.overlays.remove(it) }

        val circulo = Polygon(mapView).apply {
            points = Polygon.pointsAsCircle(centro, RADIO_BLE_METROS)
            fillPaint.color = Color.argb(38, 53, 32, 128)
            outlinePaint.color = Color.argb(140, 53, 32, 128)
            outlinePaint.strokeWidth = 3f
            infoWindow = null
        }

        val pinCliente = Marker(mapView).apply {
            position = centro
            title = context.getString(ClienteR.string.cliente_mapa_tu_ubicacion)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }

        circuloAlcanceBle = circulo
        marcadorCliente = pinCliente
        mapView.overlays.add(0, circulo)
        mapView.overlays.add(1, pinCliente)
    }

    private fun calcularPosicionEnMapa(vendedor: VendedorEnMapa): GeoPoint {
        val real = vendedor.ubicacionReal
        if (real != null && (real.lat != 0.0 || real.lng != 0.0)) {
            return GeoPoint(real.lat, real.lng)
        }
        // Si el vendedor todavía no comparte coordenadas GPS o está offline,
        // lo posicionamos alrededor del cliente usando la distancia estimada por RSSI BLE
        // y un ángulo determinista basado en su dirección BLE para evitar saltos en pantalla.
        val anguloRad = Math.toRadians((abs(vendedor.direccionBle.hashCode()) % 360).toDouble())
        val distancia = vendedor.distanciaEstimadaMetros
        val deltaLat = (distancia * cos(anguloRad)) / METROS_POR_GRADO_LAT
        val cosLat = cos(Math.toRadians(centroActual.latitude)).coerceAtLeast(0.1)
        val deltaLng = (distancia * sin(anguloRad)) / (METROS_POR_GRADO_LAT * cosLat)
        return GeoPoint(centroActual.latitude + deltaLat, centroActual.longitude + deltaLng)
    }

    companion object {
        private val PUNTO_DEFECTO = GeoPoint(19.4326, -99.1332)
        private const val ZOOM_INICIAL = 18.0
        private const val ZOOM_SELECCION = 19.0
        private const val RADIO_BLE_METROS = 45.0
        private const val METROS_POR_GRADO_LAT = 111_320.0
    }
}
