package com.ambulantitos.feature.vendedor

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import android.content.Intent
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.ambulantitos.core.network.ble.PracticaBle
import com.ambulantitos.core.network.ble.VendedorBle
import com.ambulantitos.core.network.p2p.PruebaP2p
import com.ambulantitos.core.network.p2p.WifiDirect
import java.io.IOException

class PanelVendedorActivity : ComponentActivity() {
    private val bluetooth by lazy { VendedorBle(this) }
    private var dialogoBle: AlertDialog? = null
    private var dialogoP2p: AlertDialog? = null
    private var negocioParaAnunciar: String? = null
    private val permisoP2p = registerForActivityResult(ActivityResultContracts.RequestPermission()) { autorizado ->
        if (autorizado) mostrarConexionP2p()
        else Toast.makeText(this, R.string.vendedor_p2p_permiso, Toast.LENGTH_LONG).show()
    }
    private val permisosBle = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (PracticaBle.tienePermisos(this, vendedor = true)) {
            anunciarNegocio()
        } else {
            Toast.makeText(this, R.string.vendedor_ble_permiso, Toast.LENGTH_LONG).show()
        }
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

        val botonCerrarSesion = findViewById<View>(
            R.id.vendedor_panel_btn_cerrar_sesion
        )
        val tituloNegocio = findViewById<TextView>(
            R.id.vendedor_panel_nombre_negocio
        )
        val botonBle = findViewById<View>(R.id.vendedor_panel_practica_ble)
        val botonP2p = findViewById<View>(R.id.vendedor_panel_practica_p2p)
        botonP2p.setOnClickListener {
            if (WifiDirect.tienePermiso(this)) mostrarConexionP2p()
            else permisoP2p.launch(WifiDirect.PERMISO)
        }
        botonBle.setOnClickListener {
            if (PracticaBle.tienePermisos(this, vendedor = true)) {
                anunciarNegocio()
            } else {
                permisosBle.launch(PracticaBle.permisos(vendedor = true))
            }
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
                    negocioParaAnunciar = vendedor?.nombreNegocio?.takeIf { it.isNotBlank() }
                        ?: vendedor?.usuario
                    botonBle.isEnabled = negocioParaAnunciar != null
                    botonP2p.isEnabled = vendedor != null
                    if (vendedor == null) dialogoBle?.dismiss()
                    if (vendedor == null) dialogoP2p?.dismiss()
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

    private fun anunciarNegocio() {
        val nombre = negocioParaAnunciar ?: return
        if (dialogoBle != null) return
        val dialogo = AlertDialog.Builder(this)
            .setTitle(R.string.vendedor_practica_ble)
            .setMessage("")
            .setNegativeButton(R.string.vendedor_ble_detenido, null)
            .create()
        dialogoBle = dialogo
        dialogo.setOnDismissListener {
            bluetooth.detener()
            dialogoBle = null
        }
        dialogo.show()
        dialogo.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        bluetooth.iniciar(
            nombre = nombre,
            estado = { dialogo.setMessage(it) },
            recibido = { dialogo.setMessage(getString(R.string.vendedor_ble_mensaje, it)) },
        )
    }

    private fun mostrarConexionP2p() {
        if (negocioParaAnunciar == null || dialogoP2p != null) return
        val conexion = WifiDirect(this)
        val dialogo = AlertDialog.Builder(this)
            .setTitle(R.string.vendedor_practica_p2p)
            .setMessage(R.string.vendedor_p2p_iniciando)
            .setNegativeButton(R.string.vendedor_p2p_cerrar, null)
            .show()
        dialogoP2p = dialogo
        dialogo.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val sesion = lifecycleScope.launch {
            try {
                conexion.abrir(paraBusqueda = false)
                val ip = conexion.crearGrupo()
                val nombre = conexion.nombreDelTelefono()?.takeIf { it.isNotBlank() }
                PruebaP2p.escuchar(
                    direccion = ip,
                    listo = {
                        dialogo.setMessage(if (nombre == null) getString(R.string.vendedor_p2p_listo_sin_nombre)
                        else getString(R.string.vendedor_p2p_listo, nombre))
                    },
                    recibido = { dialogo.setMessage(getString(R.string.vendedor_p2p_recibido, it)) },
                )
            } catch (error: WifiDirect.Error) {
                dialogo.setMessage(error.message)
            } catch (_: IOException) {
                dialogo.setMessage(getString(R.string.vendedor_p2p_error))
            } catch (_: SecurityException) {
                dialogo.setMessage(getString(R.string.vendedor_p2p_permiso))
            } finally {
                conexion.close()
            }
        }
        dialogo.setOnDismissListener {
            sesion.cancel()
            conexion.close()
            dialogoP2p = null
        }
    }

    override fun onStop() {
        dialogoBle?.dismiss()
        dialogoP2p?.dismiss()
        super.onStop()
    }
}
