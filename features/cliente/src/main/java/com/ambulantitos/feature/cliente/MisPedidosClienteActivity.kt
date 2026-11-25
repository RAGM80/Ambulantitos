package com.ambulantitos.feature.cliente

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.RecyclerView
import com.ambulantitos.feature.cliente.R as ClienteR
import com.google.android.material.appbar.MaterialToolbar

class MisPedidosClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Carga el diseño de Mis pedidos
        setContentView(ClienteR.layout.pantalla_mis_pedidos_cliente)

        //Recibe las indicaciones que vienen desde la confirmación
        val claveIndicaciones = RevisionPedidoClienteActivity.EXTRA_INDICACIONES
        val indicaciones = intent.getStringExtra(claveIndicaciones).orEmpty()

        //La flecha vuelve a la pantalla desde donde se abrió la lista
        val barra = findViewById<MaterialToolbar>(ClienteR.id.barra_mis_pedidos_cliente)
        barra.setNavigationOnClickListener {
            finish()
        }

        //Muestra una fila de prueba y abre su detalle al tocarla
        val lista = findViewById<RecyclerView>(ClienteR.id.lista_mis_pedidos_cliente)
        lista.adapter = PedidoPruebaAdapter {
            val intent = Intent(this, DetallePedidoClienteActivity::class.java)
            intent.putExtra(claveIndicaciones, indicaciones)
            startActivity(intent)
        }

        //Vuelve a Vendedores cercanos y cierra las pantallas del pedido
        val botonMapa = findViewById<View>(ClienteR.id.btn_volver_mapa_cliente)
        botonMapa.setOnClickListener {
            val intent = Intent(this, BusquedaClienteActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        aplicarBordes()
    }
}

private class PedidoPruebaAdapter(
    private val alTocarPedido: () -> Unit
) : RecyclerView.Adapter<PedidoPruebaAdapter.FilaPedido>() {

    //Guarda los dos textos que vamos a llenar en la tarjeta
    class FilaPedido(vista: View) : RecyclerView.ViewHolder(vista) {
        val titulo: TextView = vista.findViewById(ClienteR.id.tv_titulo_pedido_cliente)
        val resumen: TextView = vista.findViewById(ClienteR.id.tv_resumen_pedido_cliente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilaPedido {
        //Usa el XML que diseñamos para una tarjeta de pedido
        val vista = LayoutInflater.from(parent.context).inflate(
            ClienteR.layout.elemento_pedido_cliente,
            parent,
            false
        )
        return FilaPedido(vista)
    }

    override fun onBindViewHolder(holder: FilaPedido, position: Int) {
        //Llena la tarjeta con Pedido 1 - Elotitos y 1 esquite
        val contexto = holder.itemView.context
        holder.titulo.text = contexto.getString(
            ClienteR.string.cliente_demo_resumen_pedido,
            contexto.getString(ClienteR.string.cliente_demo_numero_pedido),
            contexto.getString(ClienteR.string.cliente_demo_vendedor)
        )
        holder.resumen.setText(ClienteR.string.cliente_demo_productos_pedido)

        holder.itemView.setOnClickListener {
            alTocarPedido()
        }
    }

    override fun getItemCount(): Int {
        //Solo mostramos un pedido para probar la navegación
        return 1
    }
}