package com.ambulantitos.feature.cliente

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.domain.Order
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MisPedidosClienteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.pantalla_mis_pedidos_cliente)
        findViewById<MaterialToolbar>(R.id.barra_mis_pedidos_cliente).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.btn_volver_mapa_cliente).setOnClickListener { volverABusqueda() }
        val estado = findViewById<TextView>(R.id.tv_indicacion_mis_pedidos)
        val adapter = PedidosClienteAdapter { pedido ->
            startActivity(Intent(this, DetallePedidoClienteActivity::class.java).putExtra(EXTRA_ORDER_ID, pedido.orderId))
        }
        findViewById<RecyclerView>(R.id.lista_mis_pedidos_cliente).adapter = adapter
        aplicarBordes()
        estado.setText(R.string.cliente_pedidos_cargando)
        val repositorio = RepositorioFirestore.obtener()
        val clientId = repositorio.uidActual()
        if (clientId == null) {
            estado.setText(R.string.cliente_pedido_ingresa)
            return
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repositorio.escucharPedidosCliente(clientId)
                    .catch {
                        adapter.submitList(emptyList())
                        estado.visibility = View.VISIBLE
                        estado.setText(R.string.cliente_pedidos_error)
                    }
                    .collect { pedidos ->
                        adapter.submitList(pedidos)
                        estado.setText(R.string.cliente_pedidos_vacio)
                        estado.visibility = if (pedidos.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
        }
    }
}

private class PedidosClienteAdapter(
    private val abrirPedido: (Order) -> Unit,
) : ListAdapter<Order, PedidosClienteAdapter.Fila>(object : DiffUtil.ItemCallback<Order>() {
    override fun areItemsTheSame(anterior: Order, nuevo: Order) = anterior.orderId == nuevo.orderId
    override fun areContentsTheSame(anterior: Order, nuevo: Order) = anterior == nuevo
}) {
    class Fila(vista: View) : RecyclerView.ViewHolder(vista) {
        val titulo: TextView = vista.findViewById(R.id.tv_titulo_pedido_cliente)
        val resumen: TextView = vista.findViewById(R.id.tv_resumen_pedido_cliente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Fila =
        Fila(LayoutInflater.from(parent.context).inflate(R.layout.elemento_pedido_cliente, parent, false))

    override fun onBindViewHolder(holder: Fila, position: Int) {
        val pedido = getItem(position)
        val contexto = holder.itemView.context
        holder.titulo.text = contexto.getString(R.string.cliente_pedido_negocio, contexto.tituloPedido(pedido), pedido.vendedorNombre)
        holder.resumen.text = contexto.getString(R.string.cliente_pedido_estado, pedido.estado) +
            "\n" + contexto.resumenPedido(pedido)
        holder.itemView.setOnClickListener { abrirPedido(pedido) }
    }
}
