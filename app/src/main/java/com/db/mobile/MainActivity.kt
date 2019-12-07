package com.db.mobile

import kotlinx.coroutines.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.*
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.SupplicantState
import android.provider.Settings
import android.widget.ImageButton
import androidx.core.content.FileProvider
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.*
import com.github.kittinunf.fuel.coroutines.awaitStringResponse
import com.yalantis.ucrop.UCrop
import java.io.IOException
import kotlin.concurrent.thread

const val DOCBOX_PORT="8082" // port for connection to DropBox Server
const val MAX_WIFI = 20 // maximal number of allowed Wifi Networks


class MainActivity : AppCompatActivity() {


    var cameraImageUri = Uri.EMPTY
    var validWifis = mutableListOf<String>()

    private val CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100
    internal val context: Context = this

    //************* Settings Menu **********************************************

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.action_settings_server -> {
                val intent = Intent(this, SettingsSetServer::class.java)
                startActivity(intent)
                true
            }
            R.id.action_settings_wifi -> {
                val intent = Intent(this, SettingsSetWifi::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    //**********************************************************************************
    //************* ON CREATE  *********************************************************
    //**********************************************************************************

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup listener ********** Prepare View ****************

        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        // Keep the settings parameter
        val sharedPref = getSharedPreferences("com.db.mobile.pref", Context.MODE_PRIVATE)

        val buttonScan = findViewById<ImageButton>(R.id.scann)
        val buttonUpload = findViewById<ImageButton>(R.id.upload)
        val progressBar = findViewById<ProgressBar>(R.id.determinateBar) as ProgressBar
        val statusMessage = findViewById<TextView>(R.id.statusView)

        progressBar.visibility = View.INVISIBLE
        statusMessage.text = "Scans to upload: " + Integer.toString(getUploadFiles().size)

        // **************************************************
        // Setup listener ********** Scanner ****************

        buttonScan.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                cameraImageUri = FileProvider.getUriForFile(
                    getApplicationContext(),
                    "com.db.mobile.fileprovider",
                    getFile("camera")
                )
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE)
            }
        })

        // **************************************************
        // Setup listener ********** Upload  ****************


        buttonUpload.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val server = getServerIP(sharedPref)

                if (setupWifi(sharedPref)) return

                if (checkServer(server.toString())) {

                    // we need this thread to update the user-interface in the for loopo
                    thread {

                        runOnUiThread(java.lang.Runnable {
                            progressBar.progress = 0
                            progressBar.max = getUploadFiles().size
                            progressBar.visibility = View.VISIBLE
                        })

                        for (file in getUploadFiles()) {
                            uploadFile(file)
                        }

                        runOnUiThread(java.lang.Runnable {
                            progressBar.visibility = View.INVISIBLE
                        })
                    } // end of threat

                }
                else {
                    showMessage("Error: Could not connect to DocBox Server: $server",context)
                }

            }

        }) // end of button_upload

    }

    //**********************************************************************************
    //************* ON ACTIVITY RESULT *************************************************
    //**********************************************************************************

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

// *********************** CROP ***************************************************************

        if (requestCode == UCrop.REQUEST_CROP) {

            if (data != null) {
                if (resultCode == Activity.RESULT_OK) {
                    val f_camera = File(cameraImageUri.path!!)
                    f_camera.delete()
                }
            }
        }

// *********************** SCAN **************************************************************

        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {

                val crop = UCrop.Options()
                crop.setFreeStyleCropEnabled(true)
                crop.setHideBottomControls(true)
                crop.setToolbarTitle("Crop done")

                UCrop.of(cameraImageUri, Uri.fromFile(getFile("crop")))
                    .withOptions(crop)
                    .start(this)

                // Image captured and saved to fileUri specified in the Intent

            } else if (resultCode == Activity.RESULT_CANCELED) {
                // User cancelled the image capture
            } else {
                // Image capture failed, advise user
            }
        }

        val statusMessage = findViewById<TextView>(R.id.statusView)
        statusMessage.text = "Scans to upload: " + Integer.toString(getUploadFiles().size)

    }

    //***************************************************************************************
    /// ************************** Supporting functions *********************************************
    //***************************************************************************************


    // Checks that upload is ony allowed in an known WIFI network, allowed Wifi networks are
    // stored in preferences

        private fun setupWifi(sharedPref: SharedPreferences): Boolean {
        // Check if location determination is enabled for the app ******************
        if ((context.getSystemService(Activity.LOCATION_SERVICE) as LocationManager).getProviders(
                true
            ).size == 0
        ) {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            showMessage("Grant app Permission to Location (to read SSID",context)
            return true
        }

        // Check Wifi Connection **********************************

        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiManager.connectionInfo.supplicantState != SupplicantState.COMPLETED) {
            showMessage("Error: Not connected to Wifi",context)
            return true
        }
        val wifiInfo = wifiManager.connectionInfo
        var ssid = wifiInfo.ssid

        if (ssid!!.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }

        // Read list of saved WIFI networks **********************************

        validWifis= getValidWifis(sharedPref)

        if (!validWifis.contains((ssid))) {

            val alertDialog = AlertDialog.Builder(this)
                //set icon
                .setIcon(android.R.drawable.ic_dialog_alert)
                //set title
                .setTitle("New WiFi network found!!")
                //set message
                .setMessage("Do you want to permanently allow access to: '$ssid'")
                //set positive button
                .setPositiveButton("Yes", DialogInterface.OnClickListener { dialog, i ->
                    validWifis.add(ssid)
                    saveValidWifis(sharedPref,validWifis)
                    //set what would happen when positive button is clicked
                    showMessage("WiFi $ssid saved as allowed network",context)
                })
                //set negative button
                .setNegativeButton("No", DialogInterface.OnClickListener { dialogInterface, i ->
                    //set what should happen when negative button is clicked
                    showMessage("You will not able to upload any files, as WiFi is not registered.",context)
                })
                .show()

//            showMessage("Error: Connected to $ssid, but need:${valid_wifis}",context)
            return true
        }
        return false
    }



    // gets the files that can be uploaded from local app storage
    private fun getUploadFiles(): Array<File> {
        val path = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        val files = path!!.listFiles { file ->
            file.length() > 0 && file.name.startsWith("DB_crop")
        }

        return files!!
    }

    // uploads the file from the local storage to the DocBox server
    // server name is read from preferences
    // service is running blocking to allow 2s sleep to give processing time for the server

    private fun uploadFile(file: File) {
        val progressBar = findViewById<ProgressBar>(R.id.determinateBar)
        val statusMessage = findViewById<TextView>(R.id.statusView)
        val sharedPref = getSharedPreferences("com.db.mobile.pref", Context.MODE_PRIVATE)


        val server = getServerIP(sharedPref)

        if (file.isFile) { //this line weeds out other directories/folders
            runBlocking {

                try {
                    Fuel.upload("http://$server:$DOCBOX_PORT/upload_mobile")
                        .add {
                            FileDataPart(
                                file,
                                name = "upload_file",
                                filename = file.name
                            )
                        }
                        .awaitStringResponse()
                    file.delete()
                } catch (exception: Exception) {
                    runOnUiThread(java.lang.Runnable {
                        showMessage("Could not find DocBox Server:$server", context)
                    })
                }
            }

            // sleep 2 seconds, so slow server has some time to process
            Thread.sleep(2000)

            runOnUiThread(java.lang.Runnable {
                progressBar.incrementProgressBy(1)
                statusMessage.text = "Scans to upload: " + getUploadFiles().size
            })

        }


    }

    @Throws(IOException::class)

    // generates a file-name / temp file to store the scanned picture
    private fun getFile(prefix: String): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "DB_${prefix}_JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )

    }

}

