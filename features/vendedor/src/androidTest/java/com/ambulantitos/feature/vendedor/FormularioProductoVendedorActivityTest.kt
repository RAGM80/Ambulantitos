package com.ambulantitos.feature.vendedor

import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormularioProductoVendedorActivityTest {
    @Test fun abreYConservaCamposAlRecrearSinSubirDatos() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(contexto, FormularioProductoVendedorActivity::class.java)
        ActivityScenario.launch<FormularioProductoVendedorActivity>(intent).use { escenario ->
            escenario.onActivity { actividad ->
                val campo = actividad.findViewById<EditText>(R.id.vendedor_formulario_producto_nombre)
                campo.setText("Producto de prueba local")
                val unidad = actividad.findViewById<Spinner>(R.id.vendedor_formulario_producto_unidad)
                assertEquals("pieza", unidad.selectedItem.toString())
                assertTrue(actividad.findViewById<Button>(R.id.vendedor_formulario_producto_btn_guardar).isEnabled)
            }
            escenario.recreate()
            escenario.onActivity { actividad ->
                assertEquals("Producto de prueba local", actividad.findViewById<EditText>(
                    R.id.vendedor_formulario_producto_nombre
                ).text.toString())
                assertTrue(actividad.findViewById<Button>(R.id.vendedor_formulario_producto_btn_guardar).isEnabled)
            }
        }
    }
}