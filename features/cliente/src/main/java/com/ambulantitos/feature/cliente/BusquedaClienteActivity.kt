package com.ambulantitos.feature.cliente

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar
import android.content.Intent
import android.view.View
import android.content.DialogInterface
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.ambulantitos.core.network.ble.ClienteBle
import com.ambulantitos.core.network.ble.PracticaBle
import com.ambulantitos.core.network.p2p.PruebaP2p
import com.ambulantitos.core.network.p2p.WifiDirect
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.IOException

class BusquedaClienteActivity : ComponentActivity() {
    private val bluetooth by lazy { ClienteBle(this) }
    private var dialogoBle: AlertDialog? = null
    private var dialogoP2p: AlertDialog? = null
    private val permisoP2p = registerForActivityResult(ActivityResultContracts.RequestPermission()) { autorizado ->
        if (autorizado) mostrarBusquedaP2p()
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

        //Abre el diseño de Vendedores cercanos que ya tenemos en el módulo cliente
        setContentView(ClienteR.layout.pantalla_buscar_vendedores_cliente)
        findViewById<View>(ClienteR.id.boton_busqueda_p2p).setOnClickListener {
            if (WifiDirect.tienePermiso(this)) mostrarBusquedaP2p()
            else permisoP2p.launch(WifiDirect.PERMISO)
        }
        findViewById<View>(ClienteR.id.boton_busqueda_ble).setOnClickListener {
            if (PracticaBle.tienePermisos(this, vendedor = false)) {
                mostrarBusquedaBle()
            } else {
                permisosBle.launch(PracticaBle.permisos(vendedor = false))
            }
        }

        //Busca la barra de arriba para conectar la flecha de regreso
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_busqueda)
        barra.setNavigationOnClickListener {
            //Cierra esta pantalla y vuelve al login desde donde entramos
            finish()
        }
        //Busca el botón Ver productos que está en la tarjeta del vendedor
        val botonVerProductos = findViewById<View>(ClienteR.id.boton_ver_productos)

        botonVerProductos.setOnClickListener {
            //Abre la pantalla de Productos disponibles
            val intent = Intent(this, ProductosClienteActivity::class.java)
            startActivity(intent)
        }
        //Busca el botón Mis pedidos en Vendedores cercanos
        val botonMisPedidos = findViewById<View>(ClienteR.id.boton_mis_pedidos)

        botonMisPedidos.setOnClickListener {
            //Abre la misma lista que usamos desde Pedido enviado
            val intent = Intent(this, MisPedidosClienteActivity::class.java)
            startActivity(intent)
        }
        //Deja espacio para las barras del teléfono y el teclado
        aplicarBordes()
    }

    private fun mostrarBusquedaBle() {
        if (dialogoBle != null) return
        val vendedores = linkedMapOf<String, ClienteBle.VendedorCercano>()
        val lista = ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice)
        val dialogo = AlertDialog.Builder(this)
            .setTitle(ClienteR.string.cliente_ble_buscando)
            .setSingleChoiceItems(lista, -1, null)
            .setPositiveButton(ClienteR.string.cliente_ble_enviar, null)
            .setNeutralButton(ClienteR.string.cliente_ble_buscar_de_nuevo, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialogoBle = dialogo
        dialogo.setOnDismissListener {
            bluetooth.detener()
            dialogoBle = null
        }
        dialogo.show()
        val botonEnviar = dialogo.getButton(DialogInterface.BUTTON_POSITIVE)
        val botonBuscar = dialogo.getButton(DialogInterface.BUTTON_NEUTRAL)

        fun buscar() {
            vendedores.clear()
            lista.clear()
            dialogo.listView.clearChoices()
            botonEnviar.isEnabled = false
            botonBuscar.isEnabled = false
            dialogo.setTitle(ClienteR.string.cliente_ble_buscando)
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
                    botonBuscar.isEnabled = true
                    if (error != null) {
                        dialogo.setTitle(error)
                    } else if (vendedores.isEmpty()) {
                        dialogo.setTitle(ClienteR.string.cliente_ble_sin_resultados)
                    }
                },
            )
        }

        dialogo.listView.setOnItemClickListener { _, _, _, _ -> botonEnviar.isEnabled = true }
        botonBuscar.setOnClickListener { buscar() }
        botonEnviar.setOnClickListener {
            val indice = dialogo.listView.checkedItemPosition
            val vendedor = vendedores.values.elementAtOrNull(indice) ?: return@setOnClickListener
            botonEnviar.isEnabled = false
            botonBuscar.isEnabled = false
            dialogo.listView.isEnabled = false
            dialogo.setTitle(ClienteR.string.cliente_ble_enviando)
            bluetooth.enviar(vendedor, getString(ClienteR.string.cliente_ble_mensaje)) { exito ->
                dialogo.setTitle(
                    if (exito) ClienteR.string.cliente_ble_confirmado else ClienteR.string.cliente_ble_error_envio,
                )
                dialogo.listView.isEnabled = true
                botonEnviar.isEnabled = true
                botonBuscar.isEnabled = true
            }
        }
        buscar()
    }

    private fun mostrarBusquedaP2p() {
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
            trabajo = lifecycleScope.launch {
                try {
                    busqueda?.cancelAndJoin()
                    val ip = conexion.conectar(dispositivo)
                    dialogo.setTitle(ClienteR.string.cliente_p2p_enviando)
                    PruebaP2p.enviar(ip)
                    dialogo.setTitle(ClienteR.string.cliente_p2p_confirmado)
                } catch (error: WifiDirect.Error) {
                    dialogo.setTitle(error.message)
                } catch (_: IOException) {
                    dialogo.setTitle(ClienteR.string.cliente_p2p_error)
                } catch (_: SecurityException) {
                    dialogo.setTitle(ClienteR.string.cliente_p2p_permiso)
                } finally {
                    conexion.close()
                    reintentar.isEnabled = true
                }
            }
        }
        reintentar.setOnClickListener { buscar() }
        dialogo.setOnDismissListener {
            trabajo?.cancel()
            conexion.close()
            dialogoP2p = null
        }
        buscar()
    }

    override fun onStop() {
        dialogoBle?.dismiss()
        dialogoP2p?.dismiss()
        super.onStop()
    }
}
