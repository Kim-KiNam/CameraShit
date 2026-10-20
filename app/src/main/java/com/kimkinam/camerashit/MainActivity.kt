package com.kimkinam.camerashit

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var toggle: Switch
    private lateinit var grantButton: Button
    private var updating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        toggle = findViewById(R.id.toggle)
        grantButton = findViewById(R.id.grant)

        grantButton.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        toggle.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            if (!ShutterSound.setSilent(this, checked)) {
                val detail = ShutterSound.lastError ?: getString(R.string.apply_failed)
                Toast.makeText(this, detail, Toast.LENGTH_LONG).show()
                refresh()
                status.text = detail
                return@setOnCheckedChangeListener
            }
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        ShutterSound.reapply(this)
        refresh()
    }

    private fun refresh() {
        val canWrite = ShutterSound.canWrite(this)
        val supported = ShutterSound.isSupported(this)
        updating = true
        toggle.isChecked = supported && ShutterSound.isSilent(this)
        updating = false
        toggle.isEnabled = canWrite && supported
        grantButton.visibility = if (canWrite) View.GONE else View.VISIBLE
        status.setText(
            when {
                !supported -> R.string.status_unsupported
                !canWrite -> R.string.status_need_permission
                toggle.isChecked -> R.string.status_silent
                else -> R.string.status_normal
            }
        )
    }
}
