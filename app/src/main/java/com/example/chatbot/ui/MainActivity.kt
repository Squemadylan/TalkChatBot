package com.example.chatbot.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.chatbot.R
import com.example.chatbot.ui.chat.ChatFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private var isNavInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeNavigation()
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

            navController.addOnDestinationChangedListener { _, destination, args ->
                // 同一 ChatFragment：characterId==0 为回忆列表，保留底栏；>0 为具体对话，隐藏底栏
                val characterId = args?.getLong(ChatFragment.ARG_CHARACTER_ID, 0L) ?: 0L
                val hideBottomNav =
                    destination.id == R.id.chatFragment && characterId != 0L
                bottomNav.visibility = if (hideBottomNav) View.GONE else View.VISIBLE
            }
            
            // 自定义处理导航点击事件
            bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.characterFragment -> {
                        if (navController.currentDestination?.id == R.id.chatFragment) {
                            // 如果当前在聊天页，弹出到角色页
                            navController.popBackStack(R.id.characterFragment, false)
                        } else {
                            navController.navigate(R.id.characterFragment)
                        }
                        true
                    }
                    R.id.chatFragment -> {
                        navController.navigate(R.id.chatFragment, Bundle().apply { putLong("characterId", 0L) })
                        true
                    }
                    R.id.configFragment -> {
                        navController.navigate(R.id.configFragment)
                        true
                    }
                    R.id.settingFragment -> {
                        navController.navigate(R.id.settingFragment)
                        true
                    }
                    else -> false
                }
            }
            
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
