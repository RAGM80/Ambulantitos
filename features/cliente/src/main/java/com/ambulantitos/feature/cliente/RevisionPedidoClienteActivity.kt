package com.ambulantitos.feature.cliente

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.data.ResultadoAmbulantitos
import com.ambulantitos.core.domain.FormasPago
import com.ambulantitos.core.domain.Order
import com.ambulantitos.core.domain.OrderItem
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RevisionPedidoClienteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val producto = intent.getSerializableExtra(EXTRA_PRODUCTO, ProductoPedido::class.java)
        val cantidad = intent.getIntExtra(EXTRA_CANTIDAD, 0)
        if (producto == null || cantidad !in 1..producto.existencias) {
            finish()
            return
        }
        val indicaciones = intent.getStringExtra(EXTRA_INDICACIONES).orEmpty()
        val item = producto.aItem(cantidad, indicaciones)

        enableEdgeToEdge()
        setContentView(R.layout.pantalla_revision_pedido_cliente)
        texto(R.id.tv_vendedor_revision_cliente, producto.negocio)
        texto(R.id.tv_producto_revision_cliente, item.producto)
        texto(R.id.tv_cantidad_revision_cliente, item.cantidad.toString())
        texto(R.id.tv_total_revision_cliente, dinero(item.subtotal))
        texto(R.id.tv_forma_pago_revision_cliente, FormasPago.EFECTIVO_AL_ENTREGAR)
        texto(R.id.tv_indicaciones_revision_cliente, indicaciones.ifBlank {
            getString(R.string.cliente_pedido_sin_indicaciones)
        })
        val barra = findViewById<MaterialToolbar>(R.id.barra_revision_pedido_cliente)
        val regresar = findViewById<View>(R.id.btn_regresar_revision_cliente)
        val enviar = findViewById<View>(R.id.btn_enviar_pedido_cliente)
        val estado = findViewById<TextView>(R.id.tv_numero_revision_cliente)
        val modelo = ViewModelProvider(this)[RevisionPedidoClienteViewModel::class.java]
        val bloquearSalida = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = Unit
        }
        onBackPressedDispatcher.addCallback(this, bloquearSalida)
        barra.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        regresar.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        enviar.setOnClickListener { modelo.enviar(producto, item) }
        aplicarBordes()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                modelo.estado.collect { actual ->
                    val enviando = actual == EstadoEnvioCliente.Enviando
                    enviar.isEnabled = !enviando && actual !is EstadoEnvioCliente.Enviado
                    regresar.isEnabled = !enviando
                    bloquearSalida.isEnabled = enviando
                    when (actual) {
                        EstadoEnvioCliente.Listo -> estado.setText(R.string.cliente_pedido_por_enviar)
                        EstadoEnvioCliente.Enviando -> estado.setText(R.string.cliente_pedido_enviando)
                        EstadoEnvioCliente.SinCliente -> estado.setText(R.string.cliente_pedido_ingresa)
                        is EstadoEnvioCliente.Error ->
                            estado.text = getString(R.string.cliente_pedido_error_envio, actual.mensaje)
                        is EstadoEnvioCliente.Enviado -> {
                            startActivity(Intent(
                                this@RevisionPedidoClienteActivity,
                                PedidoRecibidoClienteActivity::class.java,
                            ).putExtra(EXTRA_ORDER_ID, actual.pedido.orderId))
                            finish()
                        }
                    }
                }
            }
        }
    }
}

internal sealed interface EstadoEnvioCliente {
    data object Listo : EstadoEnvioCliente
    data object Enviando : EstadoEnvioCliente
    data object SinCliente : EstadoEnvioCliente
    data class Error(val mensaje: String) : EstadoEnvioCliente
    data class Enviado(val pedido: Order) : EstadoEnvioCliente
}

internal class RevisionPedidoClienteViewModel : ViewModel() {
    private val repositorio = RepositorioFirestore.obtener()
    private val mutable = MutableStateFlow<EstadoEnvioCliente>(EstadoEnvioCliente.Listo)
    val estado = mutable.asStateFlow()

    fun enviar(producto: ProductoPedido, item: OrderItem) {
        if (mutable.value == EstadoEnvioCliente.Enviando || mutable.value is EstadoEnvioCliente.Enviado) return
        mutable.value = EstadoEnvioCliente.Enviando
        viewModelScope.launch {
            val cliente = when (val perfil = repositorio.obtenerUsuarioActual()) {
                is ResultadoAmbulantitos.Exito -> perfil.dato.takeIf { it.esCliente }
                is ResultadoAmbulantitos.Error -> {
                    mutable.value = EstadoEnvioCliente.Error(perfil.mensaje)
                    return@launch
                }
            }
            if (cliente == null) {
                mutable.value = EstadoEnvioCliente.SinCliente
                return@launch
            }

            val vendedor = when (val negocio = repositorio.obtenerVendedor(producto.vendorId)) {
                is ResultadoAmbulantitos.Exito -> negocio.dato.nombreNegocio.ifBlank { producto.negocio }
                is ResultadoAmbulantitos.Error -> {
                    mutable.value = EstadoEnvioCliente.Error(negocio.mensaje)
                    return@launch
                }
            }
            mutable.value = when (val resultado = repositorio.crearPedido(
                clientId = cliente.userId,
                clienteNombre = cliente.nombreCompleto.ifBlank { cliente.usuario },
                vendorId = producto.vendorId,
                vendedorNombre = vendedor,
                items = listOf(item),
            )) {
                is ResultadoAmbulantitos.Exito -> EstadoEnvioCliente.Enviado(resultado.dato)
                is ResultadoAmbulantitos.Error -> EstadoEnvioCliente.Error(resultado.mensaje)
            }
        }
    }
}
