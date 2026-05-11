package com.example.chatbot.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.chatbot.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private var isNavInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            initializeNavigation()
        }
    }

    private fun initializeNavigation() {
        try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment

            if (navHostFragment == null) {
                showErrorAndFinish("Navigation component not found")
                return
            }

            val navController = navHostFragment.navController
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

            if (bottomNav == null) {
                showErrorAndFinish("Bottom navigation not found")
                return
            }

            bottomNav.setupWithNavController(navController)
            isNavInitialized = true

        } catch (e: Exception) {
            showErrorAndFinish("Failed to initialize navigation: ${e.message}")
        }
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        isNavInitialized = false
    }
}
