package com.ambulantitos.feature.cliente

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.network.ble.ClienteBle
import com.ambulantitos.core.network.ble.PracticaBle
import com.ambulantitos.core.network.p2p.CatalogoP2p
import com.ambulantitos.core.network.p2p.WifiDirect
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

class BusquedaClienteActivity : ComponentActivity() {
    private val bluetooth by lazy { ClienteBle(this) }
    private lateinit var modelo: BusquedaClienteViewModel
    private lateinit var mapa: MapaVendedoresCliente
    private lateinit var botonBusquedaBle: MaterialButton
    private lateinit var botonBusquedaP2p: MaterialButton
    private lateinit var filtroTodos: MaterialButton
    private lateinit var filtroComida: MaterialButton
    private lateinit var filtroBebidas: MaterialButton
    private lateinit var tarjetaVendedor: View
    private lateinit var textoNombreVendedor: TextView

    private val dispositivosBle = linkedMapOf<String, ClienteBle.VendedorCercano>()
    private var locationManager: LocationManager? = null
    private var escuchadorUbicacion: LocationListener? = null
    private var dialogoP2p: AlertDialog? = null
    private var conectandoP2p = false
    private var ultimoMensajeMostrado: String? = null

    private val permisoP2p = registerForActivityResult(ActivityResultContracts.RequestPermission()) { autorizado ->
        val id = modelo.estado.value.vendedorSeleccionado?.vendorId
        if (autorizado && id != null) mostrarBusquedaP2p(id)
        else Toast.makeText(this, ClienteR.string.cliente_p2p_permiso, Toast.LENGTH_LONG).show()
    }

    private val permisosBle = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (PracticaBle.tienePermisos(this, vendedor = false)) {
            iniciarBusquedaBleEnMapa()
        } else {
            Toast.makeText(this, ClienteR.string.cliente_ble_permiso, Toast.LENGTH_LONG).show()
        }
    }

    private val permisosUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resultados ->
        if (resultados.values.any { it }) {
            iniciarObservacionUbicacion()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Muestra la pantalla de Vendedores cercanos
        setContentView(ClienteR.layout.pantalla_buscar_vendedores_cliente)

        modelo = ViewModelProvider(this)[BusquedaClienteViewModel::class.java]
        botonBusquedaBle = findViewById(ClienteR.id.boton_busqueda_ble)
        botonBusquedaP2p = findViewById(ClienteR.id.boton_busqueda_p2p)
        filtroTodos = findViewById(ClienteR.id.filtro_todos)
        filtroComida = findViewById(ClienteR.id.filtro_comida)
        filtroBebidas = findViewById(ClienteR.id.filtro_bebidas)
        tarjetaVendedor = findViewById(ClienteR.id.tarjeta_vendedor)
        textoNombreVendedor = findViewById(ClienteR.id.texto_nombre_vendedor)

        savedInstanceState?.getString(EXTRA_VENDOR_ID)?.let { idGuardado ->
            val nombreGuardado = savedInstanceState.getString(EXTRA_NOMBRE_VENDEDOR).orEmpty()
            modelo.restaurarSeleccion(idGuardado, nombreGuardado)
        }

        val contenedorMapa = findViewById<ViewGroup>(ClienteR.id.contenedor_mapa)
        mapa = MapaVendedoresCliente(
            context = this,
            contenedor = contenedorMapa,
            onVendedorTocado = { vendedor -> alTocarVendedorEnMapa(vendedor) },
        )

        configurarBuscadorYFiltros()
        configurarBotones()
        observarEstado()
        solicitarOIniciarUbicacion()

        // Deja espacio para las barras del teléfono y el teclado
        aplicarBordes()
    }

    private fun configurarBuscadorYFiltros() {
        val campoBuscar = findViewById<TextInputEditText>(ClienteR.id.campo_buscar_vendedores)
        campoBuscar.doAfterTextChanged { texto ->
            modelo.actualizarTextoBusqueda(texto?.toString().orEmpty())
        }

        filtroTodos.setOnClickListener { modelo.seleccionarFiltro(FiltroCategoriaCliente.TODOS) }
        filtroComida.setOnClickListener { modelo.seleccionarFiltro(FiltroCategoriaCliente.COMIDA) }
        filtroBebidas.setOnClickListener { modelo.seleccionarFiltro(FiltroCategoriaCliente.BEBIDAS) }
    }

    private fun configurarBotones() {
        botonBusquedaP2p.setOnClickListener {
            val seleccionado = modelo.estado.value.vendedorSeleccionado?.vendorId ?: return@setOnClickListener
            if (WifiDirect.tienePermiso(this)) mostrarBusquedaP2p(seleccionado)
            else permisoP2p.launch(WifiDirect.PERMISO)
        }

        botonBusquedaBle.setOnClickListener {
            if (dialogoP2p != null) return@setOnClickListener
            if (PracticaBle.tienePermisos(this, vendedor = false)) {
                iniciarBusquedaBleEnMapa()
            } else {
                permisosBle.launch(PracticaBle.permisos(vendedor = false))
            }
        }

        // La flecha regresa a la pantalla anterior
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_busqueda)
        barra.setNavigationOnClickListener {
            finish()
        }

        // Abre Productos disponibles
        val botonVerProductos = findViewById<View>(ClienteR.id.boton_ver_productos)
        botonVerProductos.setOnClickListener {
            val seleccionado = modelo.estado.value.vendedorSeleccionado
            val id = seleccionado?.vendorId ?: return@setOnClickListener
            abrirCatalogo(id, seleccionado.nombreVisible)
        }

        // Abre la lista de pedidos
        val botonMisPedidos = findViewById<View>(ClienteR.id.boton_mis_pedidos)
        botonMisPedidos.setOnClickListener {
            startActivity(Intent(this, MisPedidosClienteActivity::class.java))
        }

        // Cierra la sesión del cliente en Firebase
        val botonCerrarSesion = findViewById<View>(ClienteR.id.boton_cerrar_sesion_cliente)
        botonCerrarSesion.setOnClickListener {
            RepositorioFirestore.obtener().cerrarSesion()
            val intent = Intent(this, LoginClienteActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }
    }

    private fun observarEstado() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                modelo.estado.collect { estado ->
                    estado.ubicacionCliente?.let { mapa.actualizarUbicacionCliente(it) }
                    mapa.actualizarVendedores(estado.vendedoresVisibles(), estado.vendedorSeleccionado)
                    actualizarEstilosFiltros(estado.filtroCategoria)

                    botonBusquedaBle.isEnabled = !estado.identificandoBle
                    botonBusquedaBle.setText(
                        when {
                            estado.identificandoBle -> ClienteR.string.cliente_ble_identificando
                            estado.buscandoBle -> ClienteR.string.cliente_ble_buscando
                            else -> ClienteR.string.cliente_buscar_vendedores
                        },
                    )

                    val seleccionado = estado.vendedorSeleccionado
                    val tieneId = !seleccionado?.vendorId.isNullOrBlank()
                    tarjetaVendedor.visibility = if (tieneId) View.VISIBLE else View.GONE
                    botonBusquedaP2p.isEnabled = tieneId
                    textoNombreVendedor.text = seleccionado?.nombreVisible.orEmpty()

                    val mensaje = estado.mensajeEstado
                    if (mensaje != null && mensaje != ultimoMensajeMostrado) {
                        ultimoMensajeMostrado = mensaje
                        Toast.makeText(this@BusquedaClienteActivity, mensaje, Toast.LENGTH_SHORT).show()
                    } else if (mensaje == null) {
                        ultimoMensajeMostrado = null
                    }
                }
            }
        }
    }

    private fun actualizarEstilosFiltros(activo: FiltroCategoriaCliente) {
        aplicarEstiloFiltro(filtroTodos, activo == FiltroCategoriaCliente.TODOS)
        aplicarEstiloFiltro(filtroComida, activo == FiltroCategoriaCliente.COMIDA)
        aplicarEstiloFiltro(filtroBebidas, activo == FiltroCategoriaCliente.BEBIDAS)
    }

    private fun aplicarEstiloFiltro(boton: MaterialButton, seleccionado: Boolean) {
        val fondo = if (seleccionado) COLOR_FILTRO_ACTIVO else COLOR_FILTRO_INACTIVO
        val texto = if (seleccionado) Color.WHITE else COLOR_FILTRO_ACTIVO
        boton.backgroundTintList = ColorStateList.valueOf(fondo)
        boton.setTextColor(texto)
    }

    private fun iniciarBusquedaBleEnMapa() {
        modelo.alIniciarBusquedaBle()
        bluetooth.buscar(
            encontrado = { vendedor ->
                dispositivosBle[vendedor.id] = vendedor
                modelo.alEncontrarVendedorBle(
                    direccionBle = vendedor.id,
                    nombreAnuncio = vendedor.nombre,
                    rssi = vendedor.rssi,
                )
            },
            terminado = { error ->
                val mensaje = when {
                    error != null -> error
                    modelo.estado.value.vendedoresDetectados.isEmpty() ->
                        getString(ClienteR.string.cliente_ble_sin_resultados)
                    else -> getString(
                        ClienteR.string.cliente_mapa_vendedores_encontrados,
                        modelo.estado.value.vendedoresDetectados.size,
                    )
                }
                modelo.alTerminarBusquedaBle(mensaje)
            },
        )
    }

    private fun alTocarVendedorEnMapa(vendedor: VendedorEnMapa) {
        mapa.centrarEnVendedor(vendedor)
        if (!vendedor.vendorId.isNullOrBlank()) {
            modelo.seleccionarVendedorYaIdentificado(vendedor)
            return
        }
        val dispositivo = dispositivosBle[vendedor.direccionBle]
        if (dispositivo == null) {
            Toast.makeText(this, ClienteR.string.cliente_ble_error_identidad, Toast.LENGTH_SHORT).show()
            return
        }
        modelo.alIniciarIdentificacion()
        bluetooth.identificar(dispositivo) { vendorId ->
            if (vendorId == null) {
                modelo.alFallarIdentificacion(getString(ClienteR.string.cliente_ble_error_identidad))
            } else {
                val nombreRespaldo = vendedor.nombreAnuncio.ifBlank {
                    getString(ClienteR.string.cliente_ble_vendedor, vendorId.takeLast(5))
                }
                modelo.alIdentificarVendedor(vendedor.direccionBle, vendorId, nombreRespaldo)
            }
        }
    }

    private fun solicitarOIniciarUbicacion() {
        if (tienePermisoUbicacion()) {
            iniciarObservacionUbicacion()
        } else {
            permisosUbicacion.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    private fun tienePermisoUbicacion(): Boolean {
        val fina = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val aproximada = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fina || aproximada
    }

    @SuppressLint("MissingPermission")
    private fun iniciarObservacionUbicacion() {
        if (!tienePermisoUbicacion()) return
        val manager = locationManager ?: getSystemService(LocationManager::class.java).also {
            locationManager = it
        } ?: return

        val proveedores = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val ultima = proveedores
            .mapNotNull { proveedor ->
                try {
                    manager.getLastKnownLocation(proveedor)
                } catch (_: SecurityException) {
                    null
                }
            }
            .maxByOrNull { it.time }

        ultima?.let { modelo.actualizarUbicacionCliente(it.latitude, it.longitude) }

        if (escuchadorUbicacion != null) return
        val listener = LocationListener { ubicacion: Location ->
            modelo.actualizarUbicacionCliente(ubicacion.latitude, ubicacion.longitude)
        }
        escuchadorUbicacion = listener

        proveedores.forEach { proveedor ->
            try {
                if (manager.isProviderEnabled(proveedor)) {
                    manager.requestLocationUpdates(proveedor, 5_000L, 5f, listener)
                }
            } catch (_: SecurityException) {
                // El permiso pudo revocarse.
            } catch (_: IllegalArgumentException) {
                // El proveedor no existe en este dispositivo.
            }
        }
    }

    private fun detenerObservacionUbicacion() {
        val listener = escuchadorUbicacion ?: return
        escuchadorUbicacion = null
        try {
            locationManager?.removeUpdates(listener)
        } catch (_: SecurityException) {
            // Se ignora si el permiso fue revocado.
        }
    }

    private fun abrirCatalogo(id: String, nombre: String, local: ByteArray? = null) {
        startActivity(Intent(this, ProductosClienteActivity::class.java).apply {
            putExtra(EXTRA_VENDOR_ID, id)
            putExtra(EXTRA_NOMBRE_VENDEDOR, nombre)
            putExtra(EXTRA_CATALOGO_LOCAL, local)
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val seleccionado = modelo.estado.value.vendedorSeleccionado
        outState.putString(EXTRA_VENDOR_ID, seleccionado?.vendorId)
        outState.putString(EXTRA_NOMBRE_VENDEDOR, seleccionado?.nombreVisible.orEmpty())
    }

    private fun mostrarBusquedaP2p(vendorId: String) {
        if (dialogoP2p != null) return
        val nombreActual = modelo.estado.value.vendedorSeleccionado?.nombreVisible.orEmpty()
        val conexion = WifiDirect(this)
        var dispositivos = emptyList<WifiDirect.Dispositivo>()
        val lista = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        val dialogo = AlertDialog.Builder(this)
            .setTitle(ClienteR.string.cliente_p2p_buscando)
            .setAdapter(lista, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(ClienteR.string.cliente_p2p_reintentar, null)
            .show()
        dialogoP2p = dialogo
        val reintentar = dialogo.getButton(DialogInterface.BUTTON_NEUTRAL)
        var trabajo: Job? = null

        fun buscar() {
            dispositivos = emptyList()
            lista.clear()
            dialogo.setTitle(ClienteR.string.cliente_p2p_buscando)
            reintentar.isEnabled = false
            dialogo.listView.isEnabled = true
            trabajo = lifecycleScope.launch {
                try {
                    conexion.close()
                    conexion.abrir(paraBusqueda = true)
                    conexion.buscar { encontrados ->
                        if (encontrados != dispositivos) {
                            dispositivos = encontrados
                            lista.clear()
                            lista.addAll(encontrados.map { dispositivo ->
                                dispositivo.nombre.ifBlank {
                                    getString(ClienteR.string.cliente_p2p_dispositivo, dispositivo.direccion.takeLast(5))
                                }
                            })
                            if (encontrados.isNotEmpty()) dialogo.setTitle(ClienteR.string.cliente_p2p_selecciona)
                        }
                    }
                    if (dispositivos.isEmpty()) dialogo.setTitle(ClienteR.string.cliente_p2p_sin_resultados)
                    reintentar.isEnabled = true
                } catch (error: IOException) {
                    dialogo.setTitle(error.message ?: getString(ClienteR.string.cliente_p2p_error))
                    reintentar.isEnabled = true
                } catch (_: SecurityException) {
                    dialogo.setTitle(ClienteR.string.cliente_p2p_permiso)
                    reintentar.isEnabled = true
                }
            }
        }

        dialogo.listView.setOnItemClickListener { _, _, posicion, _ ->
            val dispositivo = dispositivos.getOrNull(posicion) ?: return@setOnItemClickListener
            val busqueda = trabajo
            dialogo.listView.isEnabled = false
            lista.clear()
            reintentar.isEnabled = false
            dialogo.setTitle(ClienteR.string.cliente_p2p_conectando)
            conectandoP2p = true
            trabajo = lifecycleScope.launch {
                try {
                    busqueda?.cancelAndJoin()
                    val ip = conexion.conectar(dispositivo)
                    dialogo.setTitle(ClienteR.string.cliente_p2p_recibiendo)
                    val catalogo = CatalogoP2p.pedir(ip, vendorId)
                    dialogo.dismiss()
                    abrirCatalogo(vendorId, nombreActual, catalogo)
                } catch (_: CatalogoP2p.OtroVendedor) {
                    dialogo.setTitle(ClienteR.string.cliente_p2p_otro_vendedor)
                } catch (_: CatalogoP2p.SinCatalogo) {
                    dialogo.setTitle(ClienteR.string.cliente_p2p_sin_catalogo)
                } catch (error: WifiDirect.Error) {
                    dialogo.setTitle(error.message)
                } catch (_: IOException) {
                    dialogo.setTitle(ClienteR.string.cliente_p2p_error)
                } catch (_: SecurityException) {
                    dialogo.setTitle(ClienteR.string.cliente_p2p_permiso)
                } finally {
                    conexion.close()
                    conectandoP2p = false
                    reintentar.isEnabled = true
                }
            }
        }
        reintentar.setOnClickListener { buscar() }
        dialogo.setOnDismissListener {
            trabajo?.cancel()
            conexion.close()
            conectandoP2p = false
            dialogoP2p = null
        }
        buscar()
    }

    override fun onResume() {
        super.onResume()
        mapa.onResume()
        if (tienePermisoUbicacion()) iniciarObservacionUbicacion()
    }

    override fun onPause() {
        detenerObservacionUbicacion()
        mapa.onPause()
        super.onPause()
    }

    override fun onStop() {
        bluetooth.detener()
        // La invitación de Android puede cubrir la pantalla mientras se forma el grupo.
        if (!conectandoP2p) dialogoP2p?.dismiss()
        super.onStop()
    }

    override fun onDestroy() {
        detenerObservacionUbicacion()
        bluetooth.detener()
        dialogoP2p?.dismiss()
        mapa.onDestroy()
        super.onDestroy()
    }

    companion object {
        internal const val EXTRA_VENDOR_ID = "vendorId"
        internal const val EXTRA_NOMBRE_VENDEDOR = "nombreVendedor"
        internal const val EXTRA_CATALOGO_LOCAL = "catalogoLocal"
        private val COLOR_FILTRO_ACTIVO = Color.parseColor("#352080")
        private val COLOR_FILTRO_INACTIVO = Color.parseColor("#EEE7F7")
    }
}
