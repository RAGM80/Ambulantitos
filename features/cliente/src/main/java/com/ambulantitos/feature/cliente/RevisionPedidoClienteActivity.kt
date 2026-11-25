package com.ambulantitos.feature.cliente

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar
import android.content.Intent

class RevisionPedidoClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Muestra el diseño de Revisa tu pedido
        setContentView(ClienteR.layout.pantalla_revision_pedido_cliente)

        //Recibe las indicaciones que se escribieron en la pantalla anterior
        val indicaciones = intent.getStringExtra(EXTRA_INDICACIONES).orEmpty()

        //Llena el resumen con los datos de prueba del esquite
        findViewById<TextView>(ClienteR.id.tv_numero_revision_cliente)
            .setText(ClienteR.string.cliente_demo_numero_pedido)
        findViewById<TextView>(ClienteR.id.tv_vendedor_revision_cliente)
            .setText(ClienteR.string.cliente_demo_vendedor)
        findViewById<TextView>(ClienteR.id.tv_producto_revision_cliente)
            .setText(ClienteR.string.cliente_demo_nombre_esquite)
        findViewById<TextView>(ClienteR.id.tv_cantidad_revision_cliente)
            .text = 1.toString()
        findViewById<TextView>(ClienteR.id.tv_total_revision_cliente)
            .setText(ClienteR.string.cliente_demo_precio_esquite)
        findViewById<TextView>(ClienteR.id.tv_forma_pago_revision_cliente)
            .setText(ClienteR.string.cliente_busqueda_pago)

        //Si el campo quedó vacío, muestra Sin indicaciones en el resumen
        val textoIndicaciones = findViewById<TextView>(ClienteR.id.tv_indicaciones_revision_cliente)
        textoIndicaciones.text = indicaciones.ifBlank {
            getString(ClienteR.string.cliente_demo_sin_indicaciones)
        }

        //La flecha y el botón Regresar cierran el resumen
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_revision_pedido_cliente)
        barra.setNavigationOnClickListener {
            finish()
        }

        val botonRegresar = findViewById<View>(ClienteR.id.btn_regresar_revision_cliente)
        botonRegresar.setOnClickListener {
            finish()
        }
        //Busca el botón Enviar pedido que está al final del resumen
        val botonEnviar = findViewById<View>(ClienteR.id.btn_enviar_pedido_cliente)

        botonEnviar.setOnClickListener {
            //Abre la confirmación de prueba al tocar Enviar pedido
            val intent = Intent(this, PedidoRecibidoClienteActivity::class.java)
            intent.putExtra(EXTRA_INDICACIONES, indicaciones)
            startActivity(intent)
        aplicarBordes()
    }

}
    companion object {
        //Nombre que usamos para enviar y recibir las indicaciones
        const val EXTRA_INDICACIONES = "indicaciones"
    }
}