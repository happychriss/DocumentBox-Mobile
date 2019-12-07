package com.db.mobile

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitStringResponse
import kotlinx.coroutines.runBlocking

fun showMessage(message: CharSequence, my_context: Context) {

    val altDialog = AlertDialog.Builder(my_context)
    altDialog.setMessage(message) // here add your message
    altDialog.show()
}

fun checkServer(server: CharSequence): Boolean {

    var result = true

    runBlocking {
        try {
            Fuel.get("http://$server:$DOCBOX_PORT/cd_server_status_for_mobile").awaitStringResponse()
        } catch (exception: Exception) {
            result = false
        }

    }

    return result
}

fun getServerIP(sharedPref: SharedPreferences): String? {
    val server = sharedPref.getString("docboxserver", "")
    return server
}

fun getValidWifis(sharedPref: SharedPreferences): MutableList<String> {
    var my_wifis = mutableListOf<String>()
    for (i in 0..MAX_WIFI) {
        val ssid = sharedPref.getString("wifi${i}", "")
        if (ssid != "") {
            my_wifis.add(ssid.toString())
        }
    }

    return my_wifis
}

fun saveValidWifis(sharedPref: SharedPreferences, my_wifis: MutableList<String>) {

    for (i in 0 until MAX_WIFI) {

        with(sharedPref.edit()) {

            if (i < my_wifis.size) {
                putString("wifi${i}", my_wifis[i])
            } else {
                putString("wifi${i}", "")
            }

            commit()
        }

    }
}





