package com.franciscois.medtime_kotlin.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object TwilioHelper {

    // --- RELLENA TUS DATOS DE TWILIO AQUÍ ---
    // Los encuentras en tu Dashboard de Twilio
    private const val ACCOUNT_SID = "ACa9309d94b6e0e64bc941ac775c68bd6d" // Reemplaza con tu SID
    private const val AUTH_TOKEN = "f69168ba979cd4a19d1ab68dd23f89ed" // Reemplaza con tu Auth Token

    // El número de WhatsApp de la Sandbox de Twilio (el que te dieron)
    private const val TWILIO_NUMBER = "whatsapp:+14155238886"

    // El número al que quieres enviar el mensaje (DEBE ESTAR VERIFICADO EN LA SANDBOX)
    private const val TO_NUMBER = "whatsapp:+56971881015" // Reemplaza con tu número verificado

    // El ID de la plantilla que quieres usar (la de "Appointment Reminders")
    private const val CONTENT_SID = "HXb5b62575e6e4ff6129ad7c8efe1f983e" // Reemplaza con el ContentSid de tu plantilla

    // --- FIN DE LOS DATOS ---

    private val client = OkHttpClient()

    // Esta función se llama desde un hilo de fondo
    suspend fun sendWhatsAppMessage(medicationName: String, time: String) {
        // Formatear las variables para la plantilla
        // La plantilla de tu foto esperaba {{1}} (fecha) y {{2}} (hora)
        // Vamos a adaptarlo para enviar el nombre del medicamento y la hora
        val contentVariables = """
            {
                "1": "$medicationName",
                "2": "$time"
            }
        """.trimIndent()

        // 1. Construir el cuerpo del formulario
        val formBody = FormBody.Builder()
            .add("To", TO_NUMBER)
            .add("From", TWILIO_NUMBER)
            .add("ContentSid", CONTENT_SID)
            .add("ContentVariables", contentVariables)
            .build()

        // 2. Construir la URL
        val url = "https://api.twilio.com/2010-04-01/Accounts/$ACCOUNT_SID/Messages.json"

        // 3. Crear la autenticación
        val credential = Credentials.basic(ACCOUNT_SID, AUTH_TOKEN)

        // 4. Construir la petición
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .header("Authorization", credential)
            .build()

        // 5. Ejecutar la llamada en un hilo de fondo (IO)
        withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    println("✅ Mensaje de Twilio enviado exitosamente!")
                    println(response.body?.string())
                } else {
                    println("❌ Error al enviar mensaje de Twilio: ${response.code}")
                    println(response.body?.string())
                }
            } catch (e: IOException) {
                println("❌ Falla de red al llamar a Twilio: ${e.message}")
            }
        }
    }
}