package com.ambulantitos.feature.cliente

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ambulantitos.core.data.RepositorioFirestore
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class PedidoRecibidoClienteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId = intent.getStringExtra(EXTRA_ORDER_ID)
        if (orderId.isNullOrBlank()) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContentView(R.layout.pantalla_pedido_recibido_cliente)
        texto(R.id.tv_numero_recibido_cliente, getString(R.string.cliente_pedido_cargando))
        findViewById<MaterialToolbar>(R.id.barra_pedido_recibido_cliente)
            .setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.btn_volver_mapa_recibido_cliente).setOnClickListener { volverABusqueda() }
        findViewById<View>(R.id.btn_ver_pedidos_recibido_cliente).setOnClickListener {
            startActivity(Intent(this, MisPedidosClienteActivity::class.java))
        }
        aplicarBordes()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                RepositorioFirestore.obtener().escucharPedido(orderId)
                    .catch { emit(null) }
                    .collect { pedido ->
                        if (pedido == null) {
                            texto(R.id.tv_numero_recibido_cliente, getString(R.string.cliente_pedido_no_encontrado))
                            texto(R.id.tv_resumen_recibido_cliente, "")
                            texto(R.id.tv_productos_recibido_cliente, "")
                        } else {
                            texto(R.id.tv_numero_recibido_cliente, tituloPedido(pedido))
                            texto(R.id.tv_resumen_recibido_cliente, pedido.vendedorNombre)
                            texto(R.id.tv_productos_recibido_cliente, resumenPedido(pedido))
                        }
                    }
            }
        }
    }
}
