package com.example.testnativeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.*
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.time.ExperimentalTime


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermissions()
    }

    private fun showUsbActivity() {
        startActivity(Intent(this, UsbActivity::class.java))
        finish()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermission()
        } else {
            showUsbActivity()
        }
    }

    private fun requestPermission() {
        resultLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun showPermissionRationale() {
        val permissionRationaleDialog = AlertDialog.Builder(this)
            .setTitle("Enable permission")
            .setMessage("Requested permission is required for application work")
            .setNegativeButton("Decline", null)
            .setPositiveButton("Accept") { _, _ ->
                requestPermission()
            }
            .create()
        permissionRationaleDialog.show()
    }

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted -> {
                showUsbActivity()
                return@registerForActivityResult
            }
            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                showPermissionRationale()
            }
            else -> {
                finish()
            }
        }
    }
}
