package com.ambulantitos.feature.vendedor

import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.FutureTask
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetalleProductoVendedorActivityTest {
    @Test fun detalleSinIdentificadorMuestraErrorSinConsultarFirebase() {
        val instrumentacion = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentacion.targetContext, DetalleProductoVendedorActivity::class.java)
        ActivityScenario.launch<DetalleProductoVendedorActivity>(intent).use { escenario ->
            instrumentacion.waitForIdleSync()
            escenario.onActivity { actividad ->
                assertTrue(actividad.findViewById<TextView>(R.id.vendedor_detalle_producto_actualizacion)
                    .text.contains("identificador"))
                assertFalse(actividad.findViewById<Button>(R.id.vendedor_detalle_producto_btn_eliminar).isEnabled)
            }
        }
    }

    @Test fun dialogoDeConfirmacionTieneBotonesYPermiteCancelar() {
        val instrumentacion = InstrumentationRegistry.getInstrumentation()
        val comprobar = FutureTask<Unit> {
            val contexto = ContextThemeWrapper(instrumentacion.targetContext, android.R.style.Theme_Material_Light_NoActionBar)
            val dialogo = LayoutInflater.from(contexto).inflate(R.layout.vendedor_dialogo_confirmacion, null, false)
            assertNotNull(dialogo.findViewById<TextView>(R.id.vendedor_dialogo_titulo))
            assertNotNull(dialogo.findViewById<Button>(R.id.vendedor_dialogo_btn_confirmar))
            val cancelar = dialogo.findViewById<Button>(R.id.vendedor_dialogo_btn_cancelar)
            var cancelado = false
            cancelar.setOnClickListener { cancelado = true }
            cancelar.performClick()
            assertTrue(cancelado)
        }
        instrumentacion.runOnMainSync(comprobar)
        comprobar.get()
    }
}