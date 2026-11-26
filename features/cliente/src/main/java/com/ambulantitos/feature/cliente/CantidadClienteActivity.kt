package com.ambulantitos.feature.cliente

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.google.android.material.appbar.MaterialToolbar

class CantidadClienteActivity : ComponentActivity() {
    private var cantidad = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val producto = intent.getSerializableExtra(EXTRA_PRODUCTO, ProductoPedido::class.java)
        if (producto == null || producto.existencias < 1) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.pantalla_cantidad_indicaciones_cliente)
        val barra = findViewById<MaterialToolbar>(R.id.barra_cantidad_indicaciones_cliente)
        barra.title = producto.nombre
        barra.setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.btn_regresar_cantidad_cliente).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.imagen_producto_cantidad_cliente)
            .mostrarFotoProducto(producto.imagenUrl, producto.miniatura)

        texto(R.id.tv_precio_unitario_cliente, getString(R.string.cliente_pedido_precio_unitario, dinero(producto.precio)))
        val restar = findViewById<View>(R.id.btn_restar_cantidad_cliente)
        val sumar = findViewById<View>(R.id.btn_sumar_cantidad_cliente)
        cantidad = (savedInstanceState?.getInt(EXTRA_CANTIDAD) ?: 1).coerceIn(1, producto.existencias)
        fun actualizar() {
            texto(R.id.tv_cantidad_producto_cliente, cantidad.toString())
            texto(R.id.tv_total_cantidad_cliente, dinero(producto.precio * cantidad))
            restar.isEnabled = cantidad > 1
            sumar.isEnabled = cantidad < producto.existencias
        }
        restar.setOnClickListener {
            cantidad--
            actualizar()
        }
        sumar.setOnClickListener {
            cantidad++
            actualizar()
        }
        actualizar()

        val indicaciones = findViewById<EditText>(R.id.campo_indicaciones_cliente)
        findViewById<View>(R.id.btn_revisar_pedido_cliente).setOnClickListener {
            startActivity(Intent(this, RevisionPedidoClienteActivity::class.java).apply {
                putExtra(EXTRA_PRODUCTO, producto)
                putExtra(EXTRA_CANTIDAD, cantidad)
                putExtra(EXTRA_INDICACIONES, indicaciones.text.toString().trim())
            })
        }
        aplicarBordes()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_CANTIDAD, cantidad)
    }
}
