package com.ambulantitos.feature.cliente

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class LoginClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Muestra el formulario de inicio de sesión de Cliente
        setContentView(R.layout.pantalla_login_cliente)

        //Abre Vendedores cercanos al tocar Iniciar sesión
        val botonEntrar = findViewById<View>(R.id.btn_login_cliente)
        botonEntrar.setOnClickListener {
            val intent = Intent(this, BusquedaClienteActivity::class.java)
            startActivity(intent)
        }

        //Abre Registro al tocar Crear cuenta nueva
        val crearCuenta = findViewById<View>(R.id.tv_registro_cliente)
        crearCuenta.setOnClickListener {
            val intent = Intent(this, RegistroClienteActivity::class.java)
            startActivity(intent)
        }

        //Abre Recuperar cuenta al tocar Olvidé mi contraseña
        val botonRecuperar = findViewById<View>(R.id.boton_recuperar_cuenta_cliente)
        botonRecuperar.setOnClickListener {
            val intent = Intent(this, RecuperarCuentaClienteActivity::class.java)
            startActivity(intent)
        }

        aplicarBordes()
    }
}