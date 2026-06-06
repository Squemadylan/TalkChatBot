package com.example.chatbot.ui

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.chatbot.App
import com.example.chatbot.R
import com.example.chatbot.push.NotificationPermissionHelper
import com.example.chatbot.ui.chat.ChatFragment
import com.example.chatbot.util.AppUpdateManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private var isNavInitialized = false
    private var startupUpdateCheckDone = false
    private var hasRequestedNotificationPermission = false
    private var notificationPermissionDialogShown = false

    // 通知权限请求Launcher
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "通知权限已开启", Toast.LENGTH_SHORT).show()
            } else {
                // 如果用户拒绝了，显示引导用户手动开启的对话框
                if (!notificationPermissionDialogShown) {
                    showNotificationPermissionRationale()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyStatusBarSettings()
        initializeNavigation()
        // 检查并请求通知权限
        checkAndRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        // 标记APP在前台
        App.isAppInForeground = true
        
        applyStatusBarSettings()
        if (!startupUpdateCheckDone) {
            startupUpdateCheckDone = true
            AppUpdateManager.runStartupCheck(this)
        }
        // 每次回到前台时检查通知权限状态
        if (NotificationPermissionHelper.shouldRequestPermission() && 
            !NotificationPermissionHelper.areNotificationsEnabled(this) && 
            !hasRequestedNotificationPermission) {
            checkAndRequestNotificationPermission()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // 标记APP不在前台
        App.isAppInForeground = false
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

    fun applyStatusBarSettings() {
        val prefs = getSharedPreferences(App.PREFS_NAME, MODE_PRIVATE)
        val immersive = prefs.getBoolean(App.KEY_STATUS_BAR_IMMERSIVE, false)
        val savedNightMode = prefs.getInt(App.KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_YES)
        val lightStatusBarIcons = savedNightMode == AppCompatDelegate.MODE_NIGHT_NO

        WindowCompat.setDecorFitsSystemWindows(window, !immersive)
        findViewById<View>(R.id.main_root)?.fitsSystemWindows = !immersive
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = if (immersive) {
                Color.TRANSPARENT
            } else {
                ContextCompat.getColor(this, R.color.dark_background)
            }
            window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_bottom_nav)
        }
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            lightStatusBarIcons
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    /**
     * 检查并请求通知权限
     */
    private fun checkAndRequestNotificationPermission() {
        // 如果已经有通知权限，或者不需要请求权限，直接返回
        if (NotificationPermissionHelper.areNotificationsEnabled(this)) {
            return
        }
        
        // 如果是第一次请求权限，显示说明对话框
        if (!hasRequestedNotificationPermission) {
            showFirstTimePermissionExplanation()
        } else {
            // 已经请求过了，直接请求
            tryRequestNotificationPermission()
        }
    }

    /**
     * 第一次请求权限时的说明对话框
     */
    private fun showFirstTimePermissionExplanation() {
        AlertDialog.Builder(this)
            .setTitle("开启通知权限")
            .setMessage("开启通知权限后，您将及时收到AI回复和记忆保存的通知提醒")
            .setPositiveButton("去开启") { _, _ ->
                hasRequestedNotificationPermission = true
                tryRequestNotificationPermission()
            }
            .setNegativeButton("稍后再说", null)
            .show()
    }

    /**
     * 实际请求通知权限
     */
    private fun tryRequestNotificationPermission() {
        val permission = NotificationPermissionHelper.getRequiredPermission()
        if (permission != null) {
            requestNotificationPermissionLauncher.launch(permission)
        }
    }

    /**
     * 用户拒绝权限后的引导对话框
     */
    private fun showNotificationPermissionRationale() {
        notificationPermissionDialogShown = true
        AlertDialog.Builder(this)
            .setTitle("需要通知权限")
            .setMessage("通知权限被拒绝。您可以前往设置页面手动开启，以便及时收到消息提醒。")
            .setPositiveButton("去设置") { _, _ ->
                NotificationPermissionHelper.openNotificationSettings(this)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        isNavInitialized = false
    }
}
