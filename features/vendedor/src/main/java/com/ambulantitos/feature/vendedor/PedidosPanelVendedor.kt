package com.ambulantitos.feature.vendedor

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.data.ResultadoAmbulantitos
import com.ambulantitos.core.domain.EstadoPedido
import com.ambulantitos.core.domain.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class PedidosPanelVendedor(private val pantalla: PanelVendedorActivity) {
    private val repositorio = RepositorioFirestore.obtener()
    private val vendedor = MutableStateFlow<String?>(null)
    private val buscador = pantalla.findViewById<EditText>(R.id.vendedor_panel_buscar_pedido)
    private val lista = pantalla.findViewById<RecyclerView>(R.id.vendedor_panel_lista_pedidos)
    private val aviso = pantalla.findViewById<View>(R.id.vendedor_panel_estado_vacio)
    private val mensaje = pantalla.findViewById<TextView>(R.id.vendedor_panel_mensaje_pedidos)
    private val adapter = PedidosVendedorAdapter(::mostrarPedido)
    private val actualizando = mutableSetOf<String>()
    private var pedidos = emptyList<Order>()
    private var estadoVacio = R.string.vendedor_panel_sin_pedidos

    init {
        lista.adapter = adapter
        buscador.doAfterTextChanged { actualizarLista() }
        pantalla.lifecycleScope.launch {
            pantalla.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vendedor.collectLatest { id ->
                    pedidos = emptyList()
                    estadoVacio = if (id == null) R.string.vendedor_panel_sin_pedidos else R.string.vendedor_pedidos_cargando
                    actualizarLista()
                    if (id == null) return@collectLatest
                    repositorio.escucharPedidosVendedor(id)
                        .catch {
                            pedidos = emptyList()
                            estadoVacio = R.string.vendedor_pedidos_error
                            actualizarLista()
                        }
                        .collect {
                            pedidos = it
                            estadoVacio = R.string.vendedor_panel_sin_pedidos
                            actualizarLista()
                        }
                }
            }
        }
    }

    fun observar(vendorId: String?) {
        vendedor.value = vendorId
    }

    private fun actualizarLista() {
        val consulta = buscador.text.toString().trim()
        val visibles = pedidos.filter {
            consulta.isEmpty() || it.clienteNombre.contains(consulta, ignoreCase = true) ||
                it.numeroPedido.toString().contains(consulta) ||
                it.items.any { item -> item.producto.contains(consulta, ignoreCase = true) }
        }
        adapter.submitList(visibles)
        lista.visibility = if (visibles.isEmpty()) View.GONE else View.VISIBLE
        aviso.visibility = if (visibles.isEmpty()) View.VISIBLE else View.GONE
        mensaje.setText(if (visibles.isEmpty() && pedidos.isNotEmpty()) R.string.vendedor_pedidos_sin_resultados else estadoVacio)
    }

    private fun mostrarPedido(pedido: Order) {
        if (pedido.vendorId != vendedor.value || pedido.orderId in actualizando) return
        val detalle = pedido.items.joinToString("\n") {
            pantalla.getString(R.string.vendedor_pedido_linea, it.cantidad, it.producto) +
                it.indicaciones.takeIf(String::isNotBlank)?.let { nota -> "\n$nota" }.orEmpty()
        }
        val dialogo = AlertDialog.Builder(pantalla)
            .setTitle(pantalla.getString(R.string.vendedor_pedido_titulo, pedido.numeroPedido, pedido.clienteNombre))
            .setMessage(detalle + "\n\n" + pantalla.getString(R.string.vendedor_pedido_estado, pedido.estado))
            .setNegativeButton(android.R.string.cancel, null)
        when (pedido.estado) {
            EstadoPedido.PENDIENTE -> dialogo
                .setPositiveButton(R.string.vendedor_pedido_aceptar) { _, _ -> cambiarEstado(pedido, EstadoPedido.ACEPTADO) }
                .setNeutralButton(R.string.vendedor_pedido_rechazar) { _, _ -> cambiarEstado(pedido, EstadoPedido.RECHAZADO) }
            EstadoPedido.ACEPTADO -> dialogo
                .setPositiveButton(R.string.vendedor_pedido_entregar) { _, _ -> cambiarEstado(pedido, EstadoPedido.ENTREGADO) }
        }
        dialogo.show()
    }

    private fun cambiarEstado(pedido: Order, estado: String) {
        if (pedido.vendorId != vendedor.value || !actualizando.add(pedido.orderId)) return
        pantalla.lifecycleScope.launch {
            try {
                val resultado = repositorio.actualizarEstadoPedido(pedido.orderId, estado)
                if (resultado is ResultadoAmbulantitos.Error) {
                    Toast.makeText(pantalla, resultado.mensaje, Toast.LENGTH_LONG).show()
                }
            } finally {
                actualizando.remove(pedido.orderId)
            }
        }
    }
}

private class PedidosVendedorAdapter(
    private val abrir: (Order) -> Unit,
) : ListAdapter<Order, PedidosVendedorAdapter.Fila>(object : DiffUtil.ItemCallback<Order>() {
    override fun areItemsTheSame(anterior: Order, nuevo: Order) = anterior.orderId == nuevo.orderId
    override fun areContentsTheSame(anterior: Order, nuevo: Order) = anterior == nuevo
}) {
    class Fila(vista: View) : RecyclerView.ViewHolder(vista) {
        val cliente: TextView = vista.findViewById(R.id.vendedor_item_pedido_cliente)
        val hora: TextView = vista.findViewById(R.id.vendedor_item_pedido_distancia_hora)
        val resumen: TextView = vista.findViewById(R.id.vendedor_item_pedido_resumen)
        val estado: TextView = vista.findViewById(R.id.vendedor_item_pedido_estado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Fila =
        Fila(LayoutInflater.from(parent.context).inflate(R.layout.vendedor_item_pedido, parent, false))

    override fun onBindViewHolder(holder: Fila, position: Int) {
        val pedido = getItem(position)
        val contexto = holder.itemView.context
        holder.cliente.text = contexto.getString(R.string.vendedor_pedido_titulo, pedido.numeroPedido, pedido.clienteNombre)
        holder.hora.text = pedido.horaCreacion
        holder.resumen.text = pedido.items.joinToString("\n") {
            contexto.getString(R.string.vendedor_pedido_linea, it.cantidad, it.producto)
        }
        holder.estado.text = pedido.estado
        holder.itemView.setOnClickListener { abrir(pedido) }
    }
}
