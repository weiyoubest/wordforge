package com.wordforge.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.wordforge.app.R

/**
 * 主 Activity
 * 托管 NavHostFragment 和 BottomNavigationView
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == navController.currentDestination?.id) {
                return@setOnItemSelectedListener true
            }
            navController.navigate(item.itemId)
            true
        }

        // 词库初始化已在SplashActivity中通过raw SQL完成，这里只做幂等检查
    }
}
