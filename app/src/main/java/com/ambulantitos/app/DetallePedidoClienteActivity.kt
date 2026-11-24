package com.ambulantitos.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar

class DetallePedidoClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Carga el diseño que muestra los datos del pedido
        setContentView(ClienteR.layout.pantalla_detalle_pedido_cliente)

        //Llena el detalle con los mismos datos de prueba de las pantallas anteriores
        findViewById<TextView>(ClienteR.id.tv_numero_pedido_detalle)
            .setText(ClienteR.string.cliente_demo_numero_pedido)
        findViewById<TextView>(ClienteR.id.tv_vendedor_detalle)
            .setText(ClienteR.string.cliente_demo_vendedor)
        findViewById<TextView>(ClienteR.id.tv_producto_detalle)
            .setText(ClienteR.string.cliente_demo_nombre_esquite)
        findViewById<TextView>(ClienteR.id.tv_cantidad_detalle).text = 1.toString()
        findViewById<TextView>(ClienteR.id.tv_total_detalle)
            .setText(ClienteR.string.cliente_demo_precio_esquite)

        //Muestra lo que escribió el cliente o Sin indicaciones si dejó vacío el campo
        val indicaciones = intent.getStringExtra(
            RevisionPedidoClienteActivity.EXTRA_INDICACIONES
        ).orEmpty()

        findViewById<TextView>(ClienteR.id.tv_indicaciones_detalle).text =
            indicaciones.ifBlank { getString(ClienteR.string.cliente_demo_sin_indicaciones) }

        //Muestra Pendiente como estado de prueba
        findViewById<TextView>(ClienteR.id.tv_estado_pedido_detalle)
            .setText(ClienteR.string.cliente_detalle_estado_pendiente)
        findViewById<TextView>(ClienteR.id.tv_mensaje_estado_detalle)
            .setText(ClienteR.string.cliente_detalle_mensaje_pendiente)

        //La flecha y el botón Volver a pedidos cierran el detalle
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_detalle_pedido_cliente)
        barra.setNavigationOnClickListener {
            finish()
        }

        findViewById<View>(ClienteR.id.btn_volver_pedidos_cliente).setOnClickListener {
            finish()
        }

        aplicarBordes()
    }
}