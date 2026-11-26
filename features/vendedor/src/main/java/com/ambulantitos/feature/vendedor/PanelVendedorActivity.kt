package com.ambulantitos.feature.vendedor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ambulantitos.core.network.ble.PracticaBle
import com.ambulantitos.core.network.ble.VendedorBle
import com.ambulantitos.core.network.p2p.CatalogoP2p
import com.ambulantitos.core.network.p2p.WifiDirect
import java.io.IOException

class PanelVendedorActivity : ComponentActivity() {
    private val bluetooth by lazy { VendedorBle(this) }
    private lateinit var botonModoLocal: TextView
    private lateinit var estadoBle: TextView
    private lateinit var estadoP2p: TextView
    private var perfilVendedor: User? = null
    private var modoLocalActivo = false
    private var sesionP2p: Job? = null
    private val permisosModoLocal = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        iniciarModoLocal()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        setContentView(R.layout.vendedor_panel)

        aplicarBordesVendedor()
        conectarRegistroProducto(R.id.vendedor_panel_btn_registrar_producto)
        val modeloPanel = ViewModelProvider(this)[
            PanelVendedorViewModel::class.java
        ]
        val pedidos = PedidosPanelVendedor(this)

        val botonCerrarSesion = findViewById<View>(
            R.id.vendedor_panel_btn_cerrar_sesion
        )
        val tituloNegocio = findViewById<TextView>(
            R.id.vendedor_panel_nombre_negocio
        )
        botonModoLocal = findViewById(R.id.vendedor_panel_modo_local)
        estadoBle = findViewById(R.id.vendedor_panel_estado_ble)
        estadoP2p = findViewById(R.id.vendedor_panel_estado_p2p)
        botonModoLocal.setOnClickListener {
            if (modoLocalActivo) {
                detenerModoLocal()
                return@setOnClickListener
            }
            val pendientes = (PracticaBle.permisos(vendedor = true) + WifiDirect.PERMISO)
                .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (pendientes.isEmpty()) iniciarModoLocal()
            else permisosModoLocal.launch(pendientes.toTypedArray())
        }

        val errorPerfil = findViewById<TextView>(
            R.id.vendedor_panel_error_perfil
        )
        val botonReintentarPerfil = findViewById<View>(
            R.id.vendedor_panel_btn_reintentar_perfil
        )

        botonReintentarPerfil.setOnClickListener {
            modeloPanel.reintentarPerfil()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                modeloPanel.estado.collect { estado ->
                    val vendedor = (estado as? EstadoPanelVendedor.Completado)?.usuario
                    perfilVendedor = vendedor
                    val vendorId = vendedor?.idNegocio()
                    pedidos.observar(vendorId)
                    botonModoLocal.isEnabled = vendorId != null
                    if (vendedor == null) detenerModoLocal()
                    errorPerfil.visibility = View.GONE
                    botonReintentarPerfil.visibility =
                        if (estado is EstadoPanelVendedor.Error) View.VISIBLE
                        else View.GONE
                    botonReintentarPerfil.visibility = if (estado is EstadoPanelVendedor.Error) View.VISIBLE else View.GONE
                    tituloNegocio.alpha = 1f

                    when (estado) {
                        EstadoPanelVendedor.Cargando -> {
                            tituloNegocio.setText(
                                R.string.vendedor_panel_nombre_negocio
                            )
                            tituloNegocio.alpha = 0.5f
                        }

                        is EstadoPanelVendedor.Completado -> {
                            tituloNegocio.text =
                                estado.usuario.nombreNegocio
                                    ?.takeIf { it.isNotBlank() }
                                    ?: estado.usuario.usuario
                        }

                        is EstadoPanelVendedor.Error -> {
                            tituloNegocio.setText(
                                R.string.vendedor_panel_nombre_negocio
                            )
                            errorPerfil.text = estado.mensaje
                            errorPerfil.visibility = View.VISIBLE
                        }

                        EstadoPanelVendedor.SinSesion -> {
                            val abrirLogin = Intent(
                                this@PanelVendedorActivity,
                                LoginVendedorActivity::class.java
                            )

                            abrirLogin.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK

                            startActivity(abrirLogin)
                        }
                    }
                }
            }
        }
        val botonMisProductos = findViewById<View>(
            R.id.vendedor_panel_btn_mis_productos
        )

        botonMisProductos.setOnClickListener {
            val abrirProductos = Intent(
                this,
                ProductosVendedorActivity::class.java
            )

            startActivity(abrirProductos)
        }
        botonCerrarSesion.setOnClickListener {
            botonCerrarSesion.isEnabled = false
            detenerModoLocal()
            pedidos.observar(null)

            modeloPanel.cerrarSesion()

            val abrirLogin = Intent(
                this,
                LoginVendedorActivity::class.java
            )

            abrirLogin.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK

            startActivity(abrirLogin)
        }
    }

    private fun iniciarModoLocal() {
        val vendedor = perfilVendedor ?: return
        val vendorId = vendedor.idNegocio() ?: return
        val nombre = vendedor.nombreNegocio?.takeIf { it.isNotBlank() } ?: vendedor.usuario
        if (modoLocalActivo) return
        val permiteBle = PracticaBle.tienePermisos(this, vendedor = true)
        val permiteP2p = WifiDirect.tienePermiso(this)
        if (!permiteBle && !permiteP2p) {
            Toast.makeText(this, R.string.vendedor_modo_local_permiso, Toast.LENGTH_LONG).show()
            return
        }
        modoLocalActivo = true
        botonModoLocal.setText(R.string.vendedor_modo_local_detener)
        estadoBle.visibility = View.VISIBLE
        estadoP2p.visibility = View.VISIBLE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (permiteBle) {
            try {
                bluetooth.iniciar(
                    nombre = nombre,
                    vendorId = vendorId,
                    estado = { mostrarEstadoBle(it) },
                )
            } catch (_: SecurityException) {
                mostrarEstadoBle(getString(R.string.vendedor_ble_permiso))
            }
        } else {
            mostrarEstadoBle(getString(R.string.vendedor_ble_permiso))
        }
        if (permiteP2p) iniciarConexionP2p(vendorId, nombre)
        else mostrarEstadoP2p(getString(R.string.vendedor_p2p_permiso))
    }

    private fun mostrarEstadoBle(mensaje: String) {
        estadoBle.text = getString(R.string.vendedor_ble_estado, mensaje)
    }

    private fun mostrarEstadoP2p(mensaje: String) {
        estadoP2p.text = getString(R.string.vendedor_p2p_estado, mensaje)
    }

    private fun User.idNegocio(): String? =
        vendorId?.takeIf { it.isNotBlank() } ?: userId.takeIf { it.isNotBlank() }

    private fun iniciarConexionP2p(vendorId: String, nombreNegocio: String) {
        val conexion = WifiDirect(this)
        mostrarEstadoP2p(getString(R.string.vendedor_p2p_iniciando))
        sesionP2p = lifecycleScope.launch {
            try {
                conexion.abrir(paraBusqueda = false)
                val ip = conexion.crearGrupo()
                val telefono = conexion.nombreDelTelefono()?.takeIf { it.isNotBlank() }
                val mensajeListo = if (telefono == null) getString(R.string.vendedor_p2p_listo_sin_nombre)
                    else getString(R.string.vendedor_p2p_listo, telefono)
                coroutineScope {
                    var bytes: ByteArray? = null
                    var servidorListo = false
                    var catalogoConError = false
                    fun actualizarEstado() {
                        val mensaje = when {
                            catalogoConError -> getString(R.string.vendedor_p2p_catalogo_error)
                            servidorListo && bytes != null -> mensajeListo
                            else -> getString(R.string.vendedor_p2p_cargando_catalogo)
                        }
                        mostrarEstadoP2p(mensaje)
                    }
                    launch {
                        RepositorioFirestore.obtener().escucharCatalogoCliente(vendorId)
                            .catch {
                                bytes = null
                                catalogoConError = true
                                actualizarEstado()
                            }
                            .collectLatest { productos ->
                                bytes = try {
                                    withContext(Dispatchers.Default) {
                                        CatalogoP2p.codificar(CatalogoP2p.Catalogo(vendorId, nombreNegocio, productos))
                                    }
                                } catch (_: IOException) {
                                    null
                                }
                                catalogoConError = bytes == null
                                actualizarEstado()
                                if (bytes == null) return@collectLatest
                                val miniaturas = cargarFotosCatalogo(this@PanelVendedorActivity, productos)
                                if (miniaturas.isNotEmpty()) {
                                    val catalogo = CatalogoP2p.Catalogo(vendorId, nombreNegocio, productos, miniaturas)
                                    bytes = withContext(Dispatchers.Default) { CatalogoP2p.codificar(catalogo) }
                                }
                            }
                    }
                    CatalogoP2p.escuchar(ip, vendorId, contenido = { bytes }, listo = {
                        servidorListo = true
                        actualizarEstado()
                    })
                }
            } catch (error: WifiDirect.Error) {
                mostrarEstadoP2p(error.message ?: getString(R.string.vendedor_p2p_error))
            } catch (_: IOException) {
                mostrarEstadoP2p(getString(R.string.vendedor_p2p_error))
            } catch (_: SecurityException) {
                mostrarEstadoP2p(getString(R.string.vendedor_p2p_permiso))
            } finally {
                conexion.close()
            }
        }
    }

    private fun detenerModoLocal() {
        if (!modoLocalActivo) return
        modoLocalActivo = false
        bluetooth.detener()
        sesionP2p?.cancel()
        sesionP2p = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        botonModoLocal.setText(R.string.vendedor_modo_local_iniciar)
        estadoBle.visibility = View.GONE
        estadoP2p.visibility = View.GONE
    }

    override fun onDestroy() {
        detenerModoLocal()
        super.onDestroy()
    }
}
