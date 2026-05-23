/*
 * AuroraSU Manager - Main Activity
 * 
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.aurora.su.R
import com.aurora.su.databinding.ActivityMainBinding
import com.aurora.su.viewmodel.MainViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.transition.MaterialElevationScale
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply dynamic colors before super.onCreate
        DynamicColors.applyToActivityIfAvailable(this)
        
        // Install splash screen
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup splash screen exit animation
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val slideUp = ObjectAnimator.ofFloat(
                splashScreenView.view,
                View.TRANSLATION_Y,
                0f,
                -splashScreenView.view.height.toFloat()
            )
            
            slideUp.apply {
                duration = 300
                interpolator = AnticipateInterpolator()
                doOnEnd { splashScreenView.remove() }
                start()
            }
        }
        
        // Keep splash screen visible while loading
        splashScreen.setKeepOnScreenCondition { viewModel.isLoading.value }
        
        setupNavigation()
        setupStatusCard()
        observeViewModel()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        // Setup bottom navigation
        binding.bottomNav.setupWithNavController(navController)
        
        // Customize navigation behavior
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    navController.navigate(R.id.homeFragment)
                    true
                }
                R.id.nav_modules -> {
                    navController.navigate(R.id.modulesFragment)
                    true
                }
                R.id.nav_profiles -> {
                    navController.navigate(R.id.profilesFragment)
                    true
                }
                R.id.nav_settings -> {
                    navController.navigate(R.id.settingsFragment)
                    true
                }
                else -> false
            }
        }
        
        // Add elevation to bottom nav
        binding.bottomNav.background = MaterialShapeDrawable().apply {
            fillColor = android.content.res.ColorStateList.valueOf(
                SurfaceColors.SURFACE_2.getColor(this@MainActivity)
            )
            elevation = resources.getDimension(R.dimen.bottom_nav_elevation)
        }
    }

    private fun setupStatusCard() {
        // Animate status card on load
        binding.statusCard.alpha = 0f
        binding.statusCard.translationY = 50f
        
        binding.statusCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(300)
            .start()
        
        // Click to refresh status
        binding.statusCard.setOnClickListener {
            animateRefresh()
            viewModel.refreshStatus()
        }
    }

    private fun animateRefresh() {
        val refreshIcon = binding.statusCard.findViewById<ImageView>(R.id.statusIcon)
        refreshIcon?.animate()
            ?.rotationBy(360f)
            ?.setDuration(600)
            ?.start()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.kernelStatus.collect { status ->
                        updateStatusUI(status)
                    }
                }
                
                launch {
                    viewModel.moduleCount.collect { count ->
                        binding.moduleCountText.text = count.toString()
                    }
                }
                
                launch {
                    viewModel.grantedApps.collect { count ->
                        binding.grantedAppsText.text = count.toString()
                    }
                }
                
                launch {
                    viewModel.isSafeMode.collect { isSafeMode ->
                        if (isSafeMode) {
                            showSafeModeWarning()
                        }
                    }
                }
            }
        }
    }

    private fun updateStatusUI(status: KernelStatus) {
        val statusIcon = binding.statusCard.findViewById<ImageView>(R.id.statusIcon)
        val statusTitle = binding.statusCard.findViewById<TextView>(R.id.statusTitle)
        val statusSubtitle = binding.statusCard.findViewById<TextView>(R.id.statusSubtitle)
        
        when (status) {
            is KernelStatus.Working -> {
                statusIcon?.setImageResource(R.drawable.ic_check_circle_animated)
                (statusIcon?.drawable as? AnimatedVectorDrawable)?.start()
                
                statusTitle?.text = getString(R.string.status_working)
                statusTitle?.setTextColor(
                    ContextCompat.getColor(this, R.color.status_working)
                )
                
                statusSubtitle?.text = getString(
                    R.string.status_working_subtitle,
                    status.version,
                    status.hookMode
                )
                
                binding.statusCard.strokeColor = ContextCompat.getColor(
                    this, R.color.status_working_container
                )
            }
            
            is KernelStatus.NotInstalled -> {
                statusIcon?.setImageResource(R.drawable.ic_error)
                statusTitle?.text = getString(R.string.status_not_installed)
                statusTitle?.setTextColor(
                    ContextCompat.getColor(this, R.color.status_error)
                )
                statusSubtitle?.text = getString(R.string.status_not_installed_subtitle)
                binding.statusCard.strokeColor = ContextCompat.getColor(
                    this, R.color.status_error_container
                )
            }
            
            is KernelStatus.UpdateAvailable -> {
                statusIcon?.setImageResource(R.drawable.ic_update)
                statusTitle?.text = getString(R.string.status_update_available)
                statusTitle?.setTextColor(
                    ContextCompat.getColor(this, R.color.status_warning)
                )
                statusSubtitle?.text = getString(
                    R.string.status_update_subtitle,
                    status.currentVersion,
                    status.latestVersion
                )
                binding.statusCard.strokeColor = ContextCompat.getColor(
                    this, R.color.status_warning_container
                )
            }
            
            is KernelStatus.Error -> {
                statusIcon?.setImageResource(R.drawable.ic_error)
                statusTitle?.text = getString(R.string.status_error)
                statusTitle?.setTextColor(
                    ContextCompat.getColor(this, R.color.status_error)
                )
                statusSubtitle?.text = status.message
                binding.statusCard.strokeColor = ContextCompat.getColor(
                    this, R.color.status_error_container
                )
            }
        }
    }

    private fun showSafeModeWarning() {
        // Show a prominent safe mode warning
        binding.safeModeBanner.visibility = View.VISIBLE
        binding.safeModeBanner.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Reapply dynamic colors on theme change
        DynamicColors.applyToActivityIfAvailable(this)
    }
}

// Status sealed class
sealed class KernelStatus {
    data class Working(val version: String, val hookMode: String) : KernelStatus()
    object NotInstalled : KernelStatus()
    data class UpdateAvailable(val currentVersion: String, val latestVersion: String) : KernelStatus()
    data class Error(val message: String) : KernelStatus()
}
