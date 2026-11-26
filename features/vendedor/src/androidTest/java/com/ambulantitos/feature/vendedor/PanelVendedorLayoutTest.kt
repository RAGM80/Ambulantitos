package com.ambulantitos.feature.vendedor

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.FutureTask
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PanelVendedorLayoutTest {

    @Test
    fun panelSeDibujaYPermiteCambiarElInterruptorConTemaNativo() {
        val instrumentacion = InstrumentationRegistry.getInstrumentation()
        val verificacion = FutureTask<Unit> {
            val contexto = ContextThemeWrapper(
                instrumentacion.targetContext,
                android.R.style.Theme_Material_Light_NoActionBar
            )
            val panel = LayoutInflater.from(contexto).inflate(
                R.layout.vendedor_panel,
                null,
                false
            )
            val ancho = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val alto = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)

            panel.measure(ancho, alto)
            panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)

            val interruptor = panel.findViewById<SwitchCompat>(
                R.id.vendedor_panel_switch_ubicacion
            )
            assertNotNull(interruptor.thumbDrawable)
            assertNotNull(interruptor.trackDrawable)
            assertTrue(interruptor.isChecked)

            interruptor.performClick()
            panel.measure(ancho, alto)
            assertFalse(interruptor.isChecked)

            interruptor.performClick()
            panel.measure(ancho, alto)
            assertTrue(interruptor.isChecked)
        }

        instrumentacion.runOnMainSync(verificacion)
        verificacion.get()
    }
}
