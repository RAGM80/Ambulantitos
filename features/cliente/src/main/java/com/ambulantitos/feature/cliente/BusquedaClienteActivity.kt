package com.ambulantitos.feature.cliente

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.data.getOrNull
import com.ambulantitos.core.network.ble.ClienteBle
import com.ambulantitos.core.network.ble.PracticaBle
import com.ambulantitos.core.network.p2p.CatalogoP2p
import com.ambulantitos.core.network.p2p.WifiDirect
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch



class BusquedaClienteActivity : ComponentActivity() {
    private val bluetooth by lazy { ClienteBle(this) }
    private var dialogoBle: AlertDialog? = null
    private var dialogoP2p: AlertDialog? = null
    private var conectandoP2p = false
    private var vendorId: String? = null
    private var nombreVendedor: String = ""
    private val permisoP2p = registerForActivityResult(ActivityResultContracts.RequestPermission()) { autorizado ->
        if (autorizado) vendorId?.let { mostrarBusquedaP2p(it) }
        else Toast.makeText(this, ClienteR.string.cliente_p2p_permiso, Toast.LENGTH_LONG).show()
    }
    private val permisosBle = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (PracticaBle.tienePermisos(this, vendedor = false)) {
            mostrarBusquedaBle()
        } else {
            Toast.makeText(this, ClienteR.string.cliente_ble_permiso, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Muestra la pantalla de Vendedores cercanos
        setContentView(ClienteR.layout.pantalla_buscar_vendedores_cliente)
        vendorId = savedInstanceState?.getString(EXTRA_VENDOR_ID)
        nombreVendedor = savedInstanceState?.getString(EXTRA_NOMBRE_VENDEDOR).orEmpty()
        mostrarVendedorSeleccionado()
        findViewById<View>(ClienteR.id.boton_busqueda_p2p).setOnClickListener {
            val seleccionado = vendorId ?: return@setOnClickListener
            if (WifiDirect.tienePermiso(this)) mostrarBusquedaP2p(seleccionado)
            else permisoP2p.launch(WifiDirect.PERMISO)
        }
        findViewById<View>(ClienteR.id.boton_busqueda_ble).setOnClickListener {
            if (dialogoBle != null || dialogoP2p != null) return@setOnClickListener
            if (PracticaBle.tienePermisos(this, vendedor = false)) {
                mostrarBusquedaBle()
            } else {
                permisosBle.launch(PracticaBle.permisos(vendedor = false))
            }
        }

        //La flecha regresa a la pantalla anterior
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_busqueda)
        barra.setNavigationOnClickListener {
            finish()
        }

        //Abre Productos disponibles
        val botonVerProductos = findViewById<View>(ClienteR.id.boton_ver_productos)
        botonVerProductos.setOnClickListener {
            vendorId?.let { abrirCatalogo(it) }
        }

        //Abre la lista de pedidos
        val botonMisPedidos = findViewById<View>(ClienteR.id.boton_mis_pedidos)
        botonMisPedidos.setOnClickListener {
            val intent = Intent(this, MisPedidosClienteActivity::class.java)
            startActivity(intent)
        }

        //Busca el botón Cerrar sesión en el diseño
        val botonCerrarSesion = findViewById<View>(ClienteR.id.boton_cerrar_sesion_cliente)
        botonCerrarSesion.setOnClickListener {
            //Cierra la sesión del cliente en Firebase
            RepositorioFirestore.obtener().cerrarSesion()

            //Abre un login nuevo para que los campos aparezcan vacíos
            val intent = Intent(this, LoginClienteActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }

        //Deja espacio para las barras del teléfono y el teclado
        aplicarBordes()
    }

    private fun mostrarVendedorSeleccionado() {
        findViewById<View>(ClienteR.id.tarjeta_vendedor).visibility = if (vendorId == null) View.GONE else View.VISIBLE
        findViewById<View>(ClienteR.id.boton_busqueda_p2p).isEnabled = vendorId != null
        findViewById<TextView>(ClienteR.id.texto_nombre_vendedor).text = nombreVendedor
    }

    private fun abrirCatalogo(id: String, local: ByteArray? = null) {
        startActivity(Intent(this, ProductosClienteActivity::class.java).apply {
            putExtra(EXTRA_VENDOR_ID, id)
            putExtra(EXTRA_NOMBRE_VENDEDOR, nombreVendedor)
            putExtra(EXTRA_CATALOGO_LOCAL, local)
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_VENDOR_ID, vendorId)
        outState.putString(EXTRA_NOMBRE_VENDEDOR, nombreVendedor)
    }

    private fun mostrarBusquedaBle() {
        if (dialogoBle != null) return
        val vendedores = linkedMapOf<String, ClienteBle.VendedorCercano>()
        val lista = ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice)
        val dialogo = AlertDialog.Builder(this)
            .setTitle(ClienteR.string.cliente_ble_buscando)
            .setSingleChoiceItems(lista, -1, null)
            .setPositiveButton(ClienteR.string.cliente_ble_elegir, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialogoBle = dialogo
        dialogo.setOnDismissListener {
            bluetooth.detener()
            dialogoBle = null
        }
        dialogo.show()
        val elegir = dialogo.getButton(DialogInterface.BUTTON_POSITIVE)

        elegir.isEnabled = false
        dialogo.listView.setOnItemClickListener { _, _, _, _ -> elegir.isEnabled = true }
        elegir.setOnClickListener {
            val indice = dialogo.listView.checkedItemPosition
            val vendedor = vendedores.values.elementAtOrNull(indice) ?: return@setOnClickListener
            elegir.isEnabled = false
            dialogo.listView.isEnabled = false
            dialogo.setTitle(ClienteR.string.cliente_ble_identificando)
            bluetooth.identificar(vendedor) { id ->
                if (id == null) {
                    dialogo.setTitle(ClienteR.string.cliente_ble_error_identidad)
                    dialogo.listView.isEnabled = true
                    elegir.isEnabled = true
                } else {
                    vendorId = id
                    nombreVendedor = vendedor.nombre.ifBlank {
                        getString(ClienteR.string.cliente_ble_vendedor, id.takeLast(5))
                    }
                    mostrarVendedorSeleccionado()
                    dialogo.dismiss()
                    lifecycleScope.launch {
                        val perfil = RepositorioFirestore.obtener().obtenerVendedor(id).getOrNull()
                        if (vendorId == id && perfil != null) {
                            nombreVendedor = perfil.nombreNegocio.ifBlank { nombreVendedor }
                            mostrarVendedorSeleccionado()
                        }
                    }
                }
            }
        }
        bluetooth.buscar(
            encontrado = { vendedor ->
                val anterior = vendedores[vendedor.id]
                if (anterior == null || (vendedor.nombre.isNotBlank() && anterior.nombre != vendedor.nombre)) {
                    vendedores[vendedor.id] = vendedor
                    lista.clear()
                    lista.addAll(vendedores.values.map {
                        it.nombre.ifBlank { getString(ClienteR.string.cliente_ble_vendedor, it.id.takeLast(5)) }
                    })
                    dialogo.setTitle(ClienteR.string.cliente_ble_seleccionar)
                }
            },
            terminado = { error ->
                if (error != null) {
                    dialogo.setTitle(error)
                } else if (vendedores.isEmpty()) {
                    dialogo.setTitle(ClienteR.string.cliente_ble_sin_resultados)
                }
            },
        )
    }

    private fun mostrarBusquedaP2p(vendorId: String) {
        if (dialogoP2p != null) return
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
                    abrirCatalogo(vendorId, catalogo)
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

    override fun onStop() {
        dialogoBle?.dismiss()
        // La invitación de Android puede cubrir la pantalla mientras se forma el grupo.
        if (!conectandoP2p) dialogoP2p?.dismiss()
        super.onStop()
    }

    override fun onDestroy() {
        dialogoP2p?.dismiss()
        super.onDestroy()
    }

    companion object {
        internal const val EXTRA_VENDOR_ID = "vendorId"
        internal const val EXTRA_NOMBRE_VENDEDOR = "nombreVendedor"
        internal const val EXTRA_CATALOGO_LOCAL = "catalogoLocal"
    }
}
