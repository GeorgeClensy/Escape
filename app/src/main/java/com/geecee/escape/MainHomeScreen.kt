package com.geecee.escape

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.geecee.escape.ui.theme.EscapeTheme
import com.geecee.escape.ui.views.HomeScreenPageManager
import com.geecee.escape.ui.views.Onboarding
import com.geecee.escape.ui.views.Settings
import com.geecee.escape.utils.ChallengesManager
import com.geecee.escape.utils.FavoriteAppsManager
import com.geecee.escape.utils.HiddenAppsManager
import com.geecee.escape.utils.PrivateSpaceStateReceiver
import com.geecee.escape.utils.ScreenTimeManager
import com.geecee.escape.utils.getBooleanSetting
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class MainAppModel(
    var context: Context,
    var packageManager: PackageManager,
    var favoriteAppsManager: FavoriteAppsManager,
    var hiddenAppsManager: HiddenAppsManager,
    var challengesManager: ChallengesManager,
    var isAppOpened: Boolean,
    var currentPackageName: String? = null,
    var coroutineScope: CoroutineScope,
    var showPrivateSpaceUnlockedUI: MutableState<Boolean>
)

@Suppress("DEPRECATION")
class MainHomeScreen : ComponentActivity() {
    private lateinit var mainAppModel: MainAppModel
    private lateinit var privateSpaceReceiver: PrivateSpaceStateReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        // Setup analytics
        configureAnalytics(
            getBooleanSetting(
                this,
                this.resources.getString(R.string.Analytics),
                false
            )
        )

        // Setup Splashscreen
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        animateSplashScreen(splashScreen)
        enableEdgeToEdge()
        configureFullScreenMode()
        ScreenTimeManager.initialize(this)
        setContent { SetUpContent() }

        //Private space receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            privateSpaceReceiver = PrivateSpaceStateReceiver { isUnlocked ->
                mainAppModel.showPrivateSpaceUnlockedUI.value = isUnlocked
            }
            val intentFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
            registerReceiver(privateSpaceReceiver, intentFilter)
        }
    }

    override fun onResume() {
        super.onResume()

        //Updates the screen time when you close an app
        try {
            // Update screen time on app when you come home
            if (mainAppModel.isAppOpened) {
                if (mainAppModel.currentPackageName != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        ScreenTimeManager.onAppClosed(mainAppModel.currentPackageName!!)

                        mainAppModel.currentPackageName = null
                    }
                }
                mainAppModel.isAppOpened = false
            }


        } catch (ex: Exception) {
            Log.e("ERROR", ex.toString())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(privateSpaceReceiver)
    }

    // Makes it fullscreen
    private fun configureFullScreenMode() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }

    //Splash Animation
    private fun animateSplashScreen(splashScreen: SplashScreen) {
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            // Create a scale animation (zoom effect)
            val scaleAnimation = ScaleAnimation(
                1f, 100f, // From normal size to 5x size
                1f, 100f,
                Animation.RELATIVE_TO_SELF, 0.5f, // Pivot at the center
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 1200 // Duration of the zoom animation in ms
                fillAfter = true // Retain the final state
            }

            // Create a fade-out animation
            val fadeOutAnimation = AlphaAnimation(1f, 0f).apply {
                duration = 300 // Duration of the fade-out in ms
                startOffset = 500 // Delay to start after zoom finishes
                fillAfter = true // Retain the final state
            }

            // Combine the animations
            val animationSet = AnimationSet(true).apply {
                addAnimation(scaleAnimation)
                addAnimation(fadeOutAnimation)
            }

            // Start the combined animation
            splashScreenViewProvider.view.startAnimation(animationSet)

            // Remove the splash screen view after the animation ends
            animationSet.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    splashScreenViewProvider.remove()
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }
    }

    // Set content
    @Composable
    private fun SetUpContent() {
        EscapeTheme {
            InitializeMainAppModel()
            SetupNavHost(determineStartDestination())
        }
    }

    // Sets properties to initialize MainAppModel
    @Composable
    private fun InitializeMainAppModel() {
        val context = LocalContext.current
        mainAppModel = MainAppModel(
            context = context,
            packageManager = packageManager,
            favoriteAppsManager = FavoriteAppsManager(context),
            hiddenAppsManager = HiddenAppsManager(context),
            challengesManager = ChallengesManager(context),
            false,
            coroutineScope = rememberCoroutineScope(),
            showPrivateSpaceUnlockedUI = remember { mutableStateOf(false) }
        )
    }

    // Finds which screen to start on
    private fun determineStartDestination(): String {
        return when {
            getBooleanSetting(mainAppModel.context, "FirstTime", true) -> "onboarding"
            else -> "home"
        }
    }

    // Navigation host
    @Composable
    private fun SetupNavHost(startDestination: String) {
        val navController = rememberNavController()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = androidx.compose.material3.MaterialTheme.colorScheme.background)
        ) {
            NavHost(navController, startDestination = startDestination) {
                composable("home",
                    enterTransition = { fadeIn(tween(300)) },
                    exitTransition = { fadeOut(tween(300)) }) {
                    HomeScreenPageManager(mainAppModel) { navController.navigate("settings") }
                }
                composable("settings",
                    enterTransition = { fadeIn(tween(300)) },
                    exitTransition = { fadeOut(tween(300)) }) {
                    Settings(
                        mainAppModel,
                        { navController.popBackStack() },
                        this@MainHomeScreen,
                    )
                }
                composable("onboarding",
                    enterTransition = { fadeIn(tween(900)) },
                    exitTransition = { fadeOut(tween(300)) }) {
                    Onboarding(navController, mainAppModel)
                }
            }
        }
    }
}

// Function to enable or disable analytics
fun configureAnalytics(enabled: Boolean) {
    val analytics = Firebase.analytics

    analytics.setConsent(
        mapOf(
            FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (enabled) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
        )
    )

    analytics.setAnalyticsCollectionEnabled(enabled)
}