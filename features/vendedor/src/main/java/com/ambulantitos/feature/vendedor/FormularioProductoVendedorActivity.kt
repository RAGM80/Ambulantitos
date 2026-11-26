package com.ambulantitos.feature.vendedor

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import com.ambulantitos.core.data.RepositorioImagenes
import com.ambulantitos.core.domain.EstadoProducto
import kotlinx.coroutines.launch

class FormularioProductoVendedorActivity : ComponentActivity() {

    private lateinit var modeloFormulario: FormularioProductoVendedorViewModel
    private var aviso: Toast? = null
    private var avisoVisible = false
    private var camposInicializados = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.vendedor_formulario_producto)
        aplicarBordesVendedor()
        modeloFormulario = ViewModelProvider(this)[FormularioProductoVendedorViewModel::class.java]
        conectarEdicion(savedInstanceState)
        conectarEstadoProducto()
        conectarIngredientes()
        conectarFotografia()
        conectarGuardado()
        conectarSalida()
    }

    private fun conectarEdicion(estadoAnterior: Bundle?) {
        camposInicializados = estadoAnterior?.getBoolean("campos_inicializados") ?: false
        if (!modeloFormulario.esEdicion) return
        findViewById<TextView>(R.id.vendedor_formulario_producto_titulo)
            .setText(R.string.vendedor_formulario_producto_editar_titulo)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                modeloFormulario.productoParaEditar.collect { producto ->
                    if (producto == null || camposInicializados) return@collect
                    findViewById<EditText>(R.id.vendedor_formulario_producto_nombre).setText(producto.nombre)
                    findViewById<EditText>(R.id.vendedor_formulario_producto_precio).setText(producto.precio.toString())
                    findViewById<EditText>(R.id.vendedor_formulario_producto_existencias).setText(producto.existencias.toString())
                    findViewById<EditText>(R.id.vendedor_formulario_producto_descripcion).setText(producto.descripcion)
                    findViewById<EditText>(R.id.vendedor_formulario_producto_marca).setText(producto.marca.orEmpty())
                    val opciones = resources.getStringArray(R.array.vendedor_producto_unidades)
                    findViewById<Spinner>(R.id.vendedor_formulario_producto_unidad)
                        .setSelection(opciones.indexOf(producto.unidadMedida).coerceAtLeast(0))
                    camposInicializados = true
                    if (intent.getBooleanExtra(DetalleProductoVendedorActivity.EXTRA_REPONER, false)) {
                        findViewById<EditText>(R.id.vendedor_formulario_producto_existencias).requestFocus()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("campos_inicializados", camposInicializados)
        super.onSaveInstanceState(outState)
    }

    private fun conectarEstadoProducto() {
        val botones = mapOf(
            EstadoProducto.EN_EXISTENCIA to findViewById<TextView>(
                R.id.vendedor_formulario_producto_btn_en_existencia
            ),
            EstadoProducto.AGOTADO to findViewById<TextView>(
                R.id.vendedor_formulario_producto_btn_agotado
            )
        )
        botones.forEach { (estado, boton) ->
            boton.backgroundTintList = null
            boton.setOnClickListener { modeloFormulario.seleccionarEstado(estado) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                modeloFormulario.estadoProducto.collect { seleccionado ->
                    botones.forEach { (estado, boton) ->
                        val activo = estado == seleccionado
                        boton.isSelected = activo
                        boton.setBackgroundResource(
                            if (activo) R.drawable.vendedor_login_fondo_boton
                            else R.drawable.vendedor_panel_fondo_boton_secundario
                        )
                        // Blanco en la selección y rojo oscuro en la alternativa.
                        boton.setTextColor(if (activo) Color.WHITE else Color.parseColor("#9E2412"))
                    }
                }
            }
        }
    }

    private fun conectarIngredientes() {
        val campo = findViewById<EditText>(R.id.vendedor_formulario_producto_nuevo_ingrediente)
        val agregar = findViewById<View>(R.id.vendedor_formulario_producto_btn_agregar_ingrediente)
        val contador = findViewById<TextView>(R.id.vendedor_formulario_producto_contador_ingredientes)
        val lista = findViewById<RecyclerView>(R.id.vendedor_formulario_producto_lista_ingredientes)
        val adaptador = IngredientesVendedorAdapter { modeloFormulario.quitarIngrediente(it) }
        lista.adapter = adaptador
        agregar.setOnClickListener {
            val error = modeloFormulario.agregarIngrediente(campo.text.toString())
            campo.error = error
            if (error == null) campo.text.clear() else campo.requestFocus()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                modeloFormulario.ingredientes.collect { ingredientes ->
                    adaptador.submitList(ingredientes)
                    contador.text = getString(
                        R.string.vendedor_formulario_producto_contador_formato, ingredientes.size
                    )
                    agregar.isEnabled = !modeloFormulario.guardando && ingredientes.size < 6
                }
            }
        }
    }

    private fun conectarFotografia() {
        val imagen = findViewById<ImageView>(R.id.vendedor_formulario_producto_icono)
        val etiqueta = findViewById<TextView>(R.id.vendedor_formulario_producto_icono_seleccionado)
        val selector = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null && !modeloFormulario.guardando) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    modeloFormulario.seleccionarImagen(uri.toString())
                } catch (_: SecurityException) {
                    mostrarAviso(getString(R.string.vendedor_formulario_producto_error_permiso_imagen))
                }
            }
        }
        findViewById<View>(R.id.vendedor_formulario_producto_btn_cambiar_icono).setOnClickListener {
            selector.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                modeloFormulario.imagenSeleccionada.collect { direccion ->
                    imagen.load(direccion?.let(Uri::parse)) {
                        placeholder(android.R.drawable.ic_menu_gallery)
                        error(android.R.drawable.ic_menu_gallery)
                        fallback(android.R.drawable.ic_menu_gallery)
                    }
                    etiqueta.setText(
                        if (direccion == null) R.string.vendedor_formulario_producto_imagen_indicacion
                        else R.string.vendedor_formulario_producto_imagen_seleccionada
                    )
                }
            }
        }
    }

    private fun conectarGuardado() {
        val nombre = findViewById<EditText>(R.id.vendedor_formulario_producto_nombre)
        val unidad = findViewById<Spinner>(R.id.vendedor_formulario_producto_unidad)
        val precio = findViewById<EditText>(R.id.vendedor_formulario_producto_precio)
        val existencias = findViewById<EditText>(R.id.vendedor_formulario_producto_existencias)
        val descripcion = findViewById<EditText>(R.id.vendedor_formulario_producto_descripcion)
        val marca = findViewById<EditText>(R.id.vendedor_formulario_producto_marca)
        val guardar = findViewById<Button>(R.id.vendedor_formulario_producto_btn_guardar)
        val imagenes = RepositorioImagenes(applicationContext)
        guardar.setOnClickListener {
            if (avisoVisible || modeloFormulario.guardando) return@setOnClickListener
            modeloFormulario.guardarProducto(
                nombre = nombre.text.toString(),
                unidadMedida = unidad.selectedItem?.toString().orEmpty(),
                precioTexto = precio.text.toString(),
                existenciasTexto = existencias.text.toString(),
                descripcion = descripcion.text.toString(),
                marca = marca.text.toString(),
                subirImagen = imagenes::subirImagen
            )
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                modeloFormulario.estadoGuardado.collect { estado ->
                    actualizarControles(estado, guardar)
                    when (estado) {
                        is EstadoGuardadoProductoVendedor.Error -> {
                            mostrarAviso(estado.mensaje)
                            modeloFormulario.limpiarErrorGuardado()
                        }
                        is EstadoGuardadoProductoVendedor.Completado -> {
                            Toast.makeText(
                                applicationContext,
                                if (modeloFormulario.esEdicion) R.string.vendedor_formulario_producto_actualizado
                                else R.string.vendedor_formulario_producto_registrado,
                                Toast.LENGTH_SHORT
                            ).show()
                            setResult(Activity.RESULT_OK)
                            finish()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun actualizarControles(estado: EstadoGuardadoProductoVendedor, guardar: Button) {
        val bloqueado = modeloFormulario.guardando ||
            estado is EstadoGuardadoProductoVendedor.Completado
        habilitarContenido(findViewById(android.R.id.content), !bloqueado)
        findViewById<View>(R.id.vendedor_formulario_producto_btn_agregar_ingrediente).isEnabled =
            !bloqueado && modeloFormulario.ingredientes.value.size < 6
        guardar.isEnabled = !bloqueado &&
            (!modeloFormulario.esEdicion || modeloFormulario.productoParaEditar.value != null)
        guardar.alpha = if (guardar.isEnabled) 1f else 0.5f
        guardar.setText(
            when (estado) {
                EstadoGuardadoProductoVendedor.CargandoProducto ->
                    R.string.vendedor_detalle_producto_cargando
                EstadoGuardadoProductoVendedor.SubiendoImagen ->
                    R.string.vendedor_formulario_producto_subiendo_imagen
                EstadoGuardadoProductoVendedor.GuardandoProducto ->
                    R.string.vendedor_formulario_producto_guardando
                else -> if (modeloFormulario.esEdicion) R.string.vendedor_formulario_producto_guardar_cambios
                    else R.string.vendedor_panel_registrar_producto
            }
        )
    }

    private fun habilitarContenido(vista: View, habilitado: Boolean) {
        vista.isEnabled = habilitado
        if (vista is ViewGroup) {
            for (indice in 0 until vista.childCount) {
                habilitarContenido(vista.getChildAt(indice), habilitado)
            }
        }
    }

    private fun conectarSalida() {
        val salir = View.OnClickListener { if (!modeloFormulario.guardando) finish() }
        findViewById<View>(R.id.vendedor_formulario_producto_btn_volver).setOnClickListener(salir)
        findViewById<View>(R.id.vendedor_formulario_producto_btn_cancelar).setOnClickListener(salir)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!modeloFormulario.guardando) finish()
            }
        })
    }

    private fun mostrarAviso(mensaje: String) {
        if (avisoVisible) return
        avisoVisible = true
        aviso = Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).apply {
            addCallback(object : Toast.Callback() {
                override fun onToastHidden() {
                    avisoVisible = false
                }
            })
            show()
        }
    }

    override fun onDestroy() {
        aviso?.cancel()
        super.onDestroy()
    }
}

internal fun ComponentActivity.conectarRegistroProducto(idBoton: Int) {
    findViewById<View>(idBoton).setOnClickListener {
        startActivity(Intent(this, FormularioProductoVendedorActivity::class.java))
    }
}
