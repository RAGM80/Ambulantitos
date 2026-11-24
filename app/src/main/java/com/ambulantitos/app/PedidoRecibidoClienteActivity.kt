package com.ambulantitos.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ambulantitos.feature.cliente.R as ClienteR

class PedidoRecibidoClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Muestra el diseño de Pedido enviado que ya tenemos en el módulo cliente
        setContentView(ClienteR.layout.pantalla_pedido_recibido_cliente)

        //Trae el número del pedido y el nombre del vendedor que usamos en la prueba
        val numeroPedido = getString(ClienteR.string.cliente_demo_numero_pedido)
        val vendedor = getString(ClienteR.string.cliente_demo_vendedor)

        //Muestra Pedido 1 debajo de la palomita
        val textoNumero = findViewById<TextView>(ClienteR.id.tv_numero_recibido_cliente)
        textoNumero.text = numeroPedido

        //Coloca Pedido 1 - Elotitos y 1 esquite dentro de la tarjeta
        val textoResumen = findViewById<TextView>(ClienteR.id.tv_resumen_recibido_cliente)
        textoResumen.text = getString(
            ClienteR.string.cliente_demo_resumen_pedido,
            numeroPedido,
            vendedor
        )

        val textoProductos = findViewById<TextView>(ClienteR.id.tv_productos_recibido_cliente)
        textoProductos.setText(ClienteR.string.cliente_demo_productos_pedido)

        //Conecta el botón Volver al mapa con Vendedores cercanos
        val botonVolver = findViewById<View>(ClienteR.id.btn_volver_mapa_recibido_cliente)
        botonVolver.setOnClickListener {
            val intent = Intent(this, BusquedaClienteActivity::class.java)

            //Regresa a la búsqueda que ya estaba abierta y cierra las pantallas del pedido
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        //Abre Mis pedidos y le pasa las indicaciones recibidas desde la revisión
        val botonVerPedidos = findViewById<View>(ClienteR.id.btn_ver_pedidos_recibido_cliente)

        botonVerPedidos.setOnClickListener {
            val claveIndicaciones = RevisionPedidoClienteActivity.EXTRA_INDICACIONES
            val indicaciones = intent.getStringExtra(claveIndicaciones).orEmpty()
            val intentLista = Intent(this, MisPedidosClienteActivity::class.java)

            intentLista.putExtra(claveIndicaciones, indicaciones)
            startActivity(intentLista)
        }
        aplicarBordes()
    }
}