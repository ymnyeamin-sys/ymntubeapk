package org.schabi.newpipe

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.databinding.ActivitySplashBinding

/**
 * Launcher screen of YmnTube. Plays a short YouTube-like logo animation, shows the app
 * version and the "Made By YMN STUDIO" credit, then hands over to [MainActivity].
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var launched = false
    private val openMainRunnable = Runnable { openMain() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep the splash non-interruptible: back press does nothing while it plays.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            }
        )

        binding.splashVersion.text = getString(R.string.splash_version, BuildConfig.VERSION_NAME)
        playAnimation()
    }

    private fun playAnimation() {
        val density = resources.displayMetrics.density

        binding.splashLogo.apply {
            alpha = 0f
            scaleX = 0.4f
            scaleY = 0.4f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(LOGO_DURATION)
                .setInterpolator(OvershootInterpolator(OVERSHOOT_TENSION))
                .withEndAction {
                    // Gentle "heartbeat" pulse once the logo has landed
                    animate()
                        .scaleX(PULSE_SCALE)
                        .scaleY(PULSE_SCALE)
                        .setDuration(PULSE_DURATION)
                        .withEndAction {
                            animate().scaleX(1f).scaleY(1f).setDuration(PULSE_DURATION).start()
                        }
                        .start()
                }
                .start()
        }

        binding.splashFooter.apply {
            alpha = 0f
            translationY = FOOTER_OFFSET_DP * density
            animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(FOOTER_DELAY)
                .setDuration(FOOTER_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        binding.root.postDelayed(openMainRunnable, TOTAL_DURATION)
    }

    private fun openMain() {
        if (launched || isFinishing) return
        launched = true

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                intent.extras?.let { putExtras(it) }
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()
    }

    override fun onDestroy() {
        binding.root.removeCallbacks(openMainRunnable)
        super.onDestroy()
    }

    private companion object {
        const val LOGO_DURATION = 700L
        const val OVERSHOOT_TENSION = 1.6f
        const val PULSE_SCALE = 1.06f
        const val PULSE_DURATION = 260L
        const val FOOTER_DELAY = 550L
        const val FOOTER_DURATION = 600L
        const val FOOTER_OFFSET_DP = 24f
        const val TOTAL_DURATION = 2200L
    }
}
