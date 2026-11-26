package com.ambulantitos.feature.vendedor

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil3.load
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import com.ambulantitos.core.domain.EstadoProducto
import com.ambulantitos.core.domain.Product
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

class DetalleProductoVendedorActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PRODUCTO_ID = "producto_id_seleccionado"
        const val EXTRA_REPONER = "enfocar_existencias"
    }

    private lateinit var modelo: DetalleProductoVendedorViewModel
    private var dialogo: Dialog? = null
    private var aviso: Toast? = null
    private val moneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.vendedor_detalle_producto)
        aplicarBordesVendedor()
        modelo = ViewModelProvider(this)[DetalleProductoVendedorViewModel::class.java]
        findViewById<View>(R.id.vendedor_detalle_producto_btn_volver).setOnClickListener { finish() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { modelo.estado.collect { mostrarEstado(it) } }
                launch {
                    modelo.aviso.collect { mensaje ->
                        if (mensaje != null) {
                            mostrarAviso(mensaje)
                            modelo.consumirAviso()
                        }
                    }
                }
            }
        }
    }

    private fun mostrarEstado(estado: EstadoDetalleProductoVendedor) {
        val indicacion = findViewById<TextView>(R.id.vendedor_detalle_producto_actualizacion)
        indicacion.visibility = View.VISIBLE
        val disponible = estado as? EstadoDetalleProductoVendedor.Disponible
        listOf(R.id.vendedor_detalle_producto_btn_editar,
            R.id.vendedor_detalle_producto_btn_marcar_agotado,
            R.id.vendedor_detalle_producto_btn_eliminar).forEach { id ->
            findViewById<View>(id).isEnabled = disponible != null && !disponible.procesando
        }
        when (estado) {
            EstadoDetalleProductoVendedor.Cargando -> indicacion.setText(R.string.vendedor_detalle_producto_cargando)
            is EstadoDetalleProductoVendedor.Error -> indicacion.text = estado.mensaje
            is EstadoDetalleProductoVendedor.Disponible -> {
                mostrarProducto(estado.producto)
                if (estado.procesando) indicacion.setText(R.string.vendedor_formulario_producto_guardando)
                else indicacion.visibility = View.GONE
            }
            EstadoDetalleProductoVendedor.Eliminado -> {
                mostrarAviso(getString(R.string.vendedor_detalle_producto_no_disponible))
                finish()
            }
            EstadoDetalleProductoVendedor.SinSesion -> {
                startActivity(Intent(this, LoginVendedorActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
        }
    }

    private fun mostrarProducto(producto: Product) {
        fun texto(id: Int, valor: String) { findViewById<TextView>(id).text = valor }
        texto(R.id.vendedor_detalle_producto_nombre, producto.nombre)
        texto(R.id.vendedor_detalle_producto_precio, moneda.format(producto.precio))
        texto(R.id.vendedor_detalle_producto_unidad, producto.unidadMedida)
        texto(R.id.vendedor_detalle_producto_existencias, producto.existencias.toString())
        texto(R.id.vendedor_detalle_producto_estado,
            if (producto.visibleEnCatalogoCliente) EstadoProducto.EN_EXISTENCIA else EstadoProducto.AGOTADO)
        texto(R.id.vendedor_detalle_producto_marca,
            producto.marca?.takeIf { it.isNotBlank() } ?: getString(R.string.vendedor_detalle_producto_sin_marca))
        texto(R.id.vendedor_detalle_producto_descripcion,
            producto.descripcion.ifBlank { getString(R.string.vendedor_detalle_producto_sin_descripcion) })
        texto(R.id.vendedor_detalle_producto_ingredientes,
            producto.ingredientes.joinToString(" · ").ifBlank { getString(R.string.vendedor_detalle_producto_sin_ingredientes) })
        findViewById<ImageView>(R.id.vendedor_detalle_producto_icono).load(producto.imagenUrl) {
            placeholder(android.R.drawable.ic_menu_gallery)
            error(android.R.drawable.ic_menu_gallery)
            fallback(android.R.drawable.ic_menu_gallery)
        }
        conectarAcciones(producto)
    }

    private fun conectarAcciones(producto: Product) {
        val editar = findViewById<Button>(R.id.vendedor_detalle_producto_btn_editar)
        val secundario = findViewById<Button>(R.id.vendedor_detalle_producto_btn_marcar_agotado)
        editar.setText(if (producto.visibleEnCatalogoCliente) R.string.vendedor_detalle_producto_editar
            else R.string.vendedor_detalle_producto_reponer)
        secundario.setText(if (producto.visibleEnCatalogoCliente) R.string.vendedor_detalle_producto_marcar_agotado
            else R.string.vendedor_detalle_producto_editar)
        editar.setOnClickListener { abrirEdicion(producto, !producto.visibleEnCatalogoCliente) }
        secundario.setOnClickListener {
            if (!producto.visibleEnCatalogoCliente) abrirEdicion(producto, false)
            else confirmar(getString(R.string.vendedor_detalle_producto_marcar_agotado),
                getString(R.string.vendedor_detalle_producto_confirmar_agotado, producto.nombre),
                getString(R.string.vendedor_detalle_producto_marcar_agotado)) { modelo.marcarAgotado() }
        }
        findViewById<View>(R.id.vendedor_detalle_producto_btn_eliminar).setOnClickListener {
            confirmar(getString(R.string.vendedor_dialogo_eliminar_titulo),
                getString(R.string.vendedor_dialogo_eliminar_mensaje, producto.nombre),
                getString(R.string.vendedor_dialogo_eliminar_confirmar)) { modelo.eliminarProducto() }
        }
    }

    private fun abrirEdicion(producto: Product, reponer: Boolean) {
        startActivity(Intent(this, FormularioProductoVendedorActivity::class.java).apply {
            putExtra(EXTRA_PRODUCTO_ID, producto.productId)
            putExtra(EXTRA_REPONER, reponer)
        })
    }

    private fun confirmar(titulo: String, mensaje: String, accion: String, alConfirmar: () -> Unit) {
        if (dialogo?.isShowing == true) return
        dialogo = Dialog(this).apply {
            setContentView(R.layout.vendedor_dialogo_confirmacion)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            findViewById<TextView>(R.id.vendedor_dialogo_titulo).text = titulo
            findViewById<TextView>(R.id.vendedor_dialogo_mensaje).text = mensaje
            findViewById<Button>(R.id.vendedor_dialogo_btn_cancelar).setOnClickListener { dismiss() }
            findViewById<Button>(R.id.vendedor_dialogo_btn_confirmar).apply {
                text = accion
                setOnClickListener { dismiss(); alConfirmar() }
            }
            show()
        }
    }

    private fun mostrarAviso(mensaje: String) {
        aviso?.cancel()
        aviso = Toast.makeText(applicationContext, mensaje, Toast.LENGTH_SHORT).also { it.show() }
    }

    override fun onDestroy() {
        dialogo?.dismiss()
        super.onDestroy()
    }
}
