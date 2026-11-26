package com.ambulantitos.feature.cliente

import android.R
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar
import android.content.Intent
import android.widget.EditText

class CantidadClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Muestra el diseño de Cantidad e indicaciones del módulo cliente
        setContentView(ClienteR.layout.pantalla_cantidad_indicaciones_cliente)

        //Muestra el nombre de prueba en la barra superior
        val barra = findViewById<MaterialToolbar>(
            ClienteR.id.barra_cantidad_indicaciones_cliente
        )
        barra.setTitle(ClienteR.string.cliente_demo_nombre_esquite)

        //Cierra esta pantalla y vuelve al catálogo al tocar la flecha
        barra.setNavigationOnClickListener {
            finish()
        }

        //Coloca una imagen de muestra en el espacio del producto
        val imagen = findViewById<ImageView>(ClienteR.id.imagen_producto_cantidad_cliente)
        imagen.setImageResource(R.drawable.ic_menu_gallery)

        //Llena los espacios del precio, la cantidad inicial y el total de prueba
        val precioDemo = getString(ClienteR.string.cliente_demo_precio_esquite)
        val precio = findViewById<TextView>(ClienteR.id.tv_precio_unitario_cliente)
        val cantidad = findViewById<TextView>(ClienteR.id.tv_cantidad_producto_cliente)
        val total = findViewById<TextView>(ClienteR.id.tv_total_cantidad_cliente)

        precio.text = getString(ClienteR.string.cliente_demo_precio_unitario, precioDemo)
        cantidad.text = 1.toString()
        total.text = precioDemo

        //El botón Regresar también cierra esta pantalla
        val botonRegresar = findViewById<View>(ClienteR.id.btn_regresar_cantidad_cliente)
        botonRegresar.setOnClickListener {
            finish()
        }
        val campoIndicaciones = findViewById<EditText>(ClienteR.id.campo_indicaciones_cliente)
        val botonRevisar = findViewById<View>(ClienteR.id.btn_revisar_pedido_cliente)

        botonRevisar.setOnClickListener {
            //Lee el texto en el momento en que el cliente toca el botón
            val indicaciones = campoIndicaciones.text.toString()

            //Abre el resumen y le manda lo que escribió el cliente
            val intent = Intent(this, RevisionPedidoClienteActivity::class.java)
            intent.putExtra(RevisionPedidoClienteActivity.EXTRA_INDICACIONES, indicaciones)
            startActivity(intent)
        }
        aplicarBordes()
    }
}