package com.ambulantitos.feature.cliente

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.domain.EstadoPedido
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class DetallePedidoClienteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId = intent.getStringExtra(EXTRA_ORDER_ID)
        if (orderId.isNullOrBlank()) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContentView(R.layout.pantalla_detalle_pedido_cliente)
        findViewById<MaterialToolbar>(R.id.barra_detalle_pedido_cliente).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.btn_volver_pedidos_cliente).setOnClickListener { finish() }
        texto(R.id.tv_mensaje_estado_detalle, getString(R.string.cliente_pedido_cargando))
        aplicarBordes()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                RepositorioFirestore.obtener().escucharPedido(orderId)
                    .catch { emit(null) }
                    .collect { pedido ->
                        if (pedido == null) {
                            texto(R.id.tv_mensaje_estado_detalle, getString(R.string.cliente_pedido_no_encontrado))
                            return@collect
                        }
                        texto(R.id.tv_numero_pedido_detalle, tituloPedido(pedido))
                        texto(R.id.tv_vendedor_detalle, pedido.vendedorNombre)
                        texto(R.id.tv_producto_detalle, pedido.items.joinToString("\n") { it.producto })
                        texto(R.id.tv_cantidad_detalle, pedido.items.joinToString("\n") { it.cantidad.toString() })
                        texto(R.id.tv_total_detalle, dinero(pedido.total))
                        texto(R.id.tv_indicaciones_detalle, pedido.items.joinToString("\n") {
                            it.indicaciones.ifBlank { getString(R.string.cliente_pedido_sin_indicaciones) }
                        })
                        texto(R.id.tv_estado_pedido_detalle, getString(R.string.cliente_pedido_estado, pedido.estado))
                        texto(R.id.tv_mensaje_estado_detalle, when {
                            pedido.estado == EstadoPedido.PENDIENTE -> getString(R.string.cliente_detalle_mensaje_pendiente)
                            else -> ""
                        })
                    }
            }
        }
    }
}
