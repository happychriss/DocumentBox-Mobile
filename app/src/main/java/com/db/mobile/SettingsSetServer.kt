package com.db.mobile

import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitStringResponse
import kotlinx.android.synthetic.main.activity_settings_set_server.*
import kotlinx.coroutines.runBlocking

//*******************************************************************************************
// Allows to set the IP address of the Upload Server
// After setting the IP address the server is validated and only valid addresses are accepted
// Valid address is stored in preference storage

class SettingsSetServer : AppCompatActivity() {
    internal val context: Context = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_set_server)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val ipAddress = findViewById<EditText>(R.id.ip_address)
        val serverStatus = findViewById<TextView>(R.id.server_status)
        serverStatus.text=""

        // Read current IP address from Preferences and update on GUI
        val sharedPref = getSharedPreferences("com.db.mobile.pref", Context.MODE_PRIVATE)
        val oldIpAddress = sharedPref.getString("docboxserver","")

        ipAddress.setText(oldIpAddress)

        // **************************************************************************
        // Update IP address if changed with new IP address

        ipAddress.setOnClickListener {

            val newIpAddress =ipAddress.text
            var error=false

            runBlocking {
                try {
                    Fuel.get("http://$newIpAddress:$DOCBOX_PORT/cd_server_status_for_mobile").awaitStringResponse()
                } catch (exception: Exception) {
                    serverStatus.text="No connection to Server"
                    error=true
                }
            }

            // if no error, update new IP address in preferences
            if (!error) {
                with (sharedPref.edit()) {
                    putString("docboxserver", newIpAddress.toString())
                    commit()
                }
                finish()
            }

        }


    }

}
