package com.db.mobile

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_settings_set_wifi.*

//*******************************************************************************************
// Lists the allowed WIFI networks and provide button to remove selected networks

class SettingsSetWifi : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_set_wifi)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val sharedPref = getSharedPreferences("com.db.mobile.pref", Context.MODE_PRIVATE)
        val listView = findViewById<ListView>(R.id.wifi_listview)
        val getChoice = findViewById<Button>(R.id.getchoice)

        // get all wifi networks currently stored in preferences
        val myWifis = getValidWifis(sharedPref)

        // we need a local copy of this list, as "array-adapter" is bound to that list and this should
        // not be changed while the adapter is active
        val myWifisItems =myWifis.toList()

        // this is a generic array addapter that is using a google standard list-type
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, myWifisItems)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE


        // ********************************************************************
        // Click event on ListBox , when a wifi network is selected
        // either add or remove from wifi-list

        listView.onItemClickListener = OnItemClickListener { parent, view, position, id ->

                val v = view as CheckedTextView
                val wifiChecked = listView.getItemAtPosition(position) as String
                if (v.isChecked) {
                    myWifis.remove(wifiChecked)
                } else {
                    myWifis.add(wifiChecked)
                }

        }

        // ********************************************************************
        // Click event on Save Button
        // Write wifi-list into preference storage

        getChoice.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                saveValidWifis(sharedPref,myWifis)
                finish()
            }

        })

    }
}