package com.ambulantitos.feature.vendedor

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import android.content.Intent
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ProductosVendedorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.vendedor_productos)

        aplicarBordesVendedor()
        conectarRegistroProducto(R.id.vendedor_productos_btn_registrar)
        val modeloProductos = ViewModelProvider(this)[
            ProductosVendedorViewModel::class.java
        ]

        val listaProductos = findViewById<RecyclerView>(
            R.id.vendedor_productos_lista
        )

        val estadoVacio = findViewById<View>(
            R.id.vendedor_productos_estado_vacio
        )

        val avisoAgotados = findViewById<View>(
            R.id.vendedor_productos_aviso_agotados
        )

        val indicacionProductos = findViewById<TextView>(
            R.id.vendedor_productos_indicacion
        )

        val adaptadorProductos = ProductosVendedorAdapter { producto ->
            startActivity(Intent(this, DetalleProductoVendedorActivity::class.java).apply {
                putExtra(DetalleProductoVendedorActivity.EXTRA_PRODUCTO_ID, producto.productId)
            })
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                modeloProductos.estado.collect { estado ->
                    listaProductos.visibility = View.GONE
                    estadoVacio.visibility = View.GONE
                    avisoAgotados.visibility = View.GONE

                    when (estado) {
                        EstadoProductosVendedor.Cargando -> {
                            indicacionProductos.setText(
                                R.string.vendedor_productos_cargando
                            )
                        }

                        is EstadoProductosVendedor.Completado -> {
                            indicacionProductos.setText(
                                R.string.vendedor_productos_indicacion
                            )

                            adaptadorProductos.submitList(estado.productos)

                            val sinProductos = estado.productos.isEmpty()

                            listaProductos.visibility =
                                if (sinProductos) View.GONE else View.VISIBLE

                            estadoVacio.visibility =
                                if (sinProductos) View.VISIBLE else View.GONE

                            val hayAgotados = estado.productos.any {
                                !it.visibleEnCatalogoCliente
                            }

                            avisoAgotados.visibility =
                                if (hayAgotados) View.VISIBLE else View.GONE
                        }

                        is EstadoProductosVendedor.Error -> {
                            indicacionProductos.text = estado.mensaje
                        }

                        EstadoProductosVendedor.SinSesion -> {
                            val abrirLogin = Intent(
                                this@ProductosVendedorActivity,
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

        listaProductos.adapter = adaptadorProductos

        val botonVolver = findViewById<View>(
            R.id.vendedor_productos_btn_volver
        )

        val botonVolverPanel = findViewById<View>(
            R.id.vendedor_productos_btn_volver_panel
        )

        val regresarAlPanel = View.OnClickListener {
            finish()
        }


        botonVolver.setOnClickListener(regresarAlPanel)
        botonVolverPanel.setOnClickListener(regresarAlPanel)
    }
}
