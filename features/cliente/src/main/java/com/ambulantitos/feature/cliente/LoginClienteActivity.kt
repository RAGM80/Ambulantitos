package com.ambulantitos.feature.cliente

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.ambulantitos.core.data.RepositorioFirestore
import com.ambulantitos.core.data.ResultadoAmbulantitos
import com.ambulantitos.core.domain.RolUsuario
import kotlinx.coroutines.launch

class LoginClienteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //Muestra el formulario de inicio de sesión de Cliente
        setContentView(R.layout.pantalla_login_cliente)

        //Busca los campos de usuario y contraseña y el botón Iniciar sesión
        val campoUsuario = findViewById<EditText>(R.id.et_usuario_cliente)
        val campoContrasena = findViewById<EditText>(R.id.et_contrasena_cliente)
        val botonEntrar = findViewById<View>(R.id.btn_login_cliente)

        botonEntrar.setOnClickListener {
            //Lee los datos escritos al tocar Iniciar sesión
            val usuario = campoUsuario.text.toString().trim()
            val contrasena = campoContrasena.text.toString()

            //Detiene el intento si falta el usuario o la contraseña
            if (usuario.isBlank() || contrasena.isBlank()) {
                Toast.makeText(
                    this,
                    "Escribe tu usuario y contraseña",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            //Evita que se mande otro intento mientras Firebase responde
            botonEntrar.isEnabled = false

            //Espera la respuesta sin congelar la pantalla
            lifecycleScope.launch {
                //Pide iniciar sesión con una cuenta de Cliente
                val resultado = RepositorioFirestore.obtener().iniciarSesion(
                    usuario = usuario,
                    contrasena = contrasena,
                    rolEsperado = RolUsuario.CLIENTE
                )
                botonEntrar.isEnabled = true

                when (resultado) {
                    is ResultadoAmbulantitos.Exito -> {
                        //Abre Vendedores cercanos si el acceso fue correcto
                        val intent = Intent(
                            this@LoginClienteActivity,
                            BusquedaClienteActivity::class.java
                        )
                        startActivity(intent)
                    }

                    is ResultadoAmbulantitos.Error -> {
                        //Muestra el aviso y deja abierto el login
                        Toast.makeText(
                            this@LoginClienteActivity,
                            "No se pudo iniciar sesión. Revisa tu usuario y contraseña.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
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