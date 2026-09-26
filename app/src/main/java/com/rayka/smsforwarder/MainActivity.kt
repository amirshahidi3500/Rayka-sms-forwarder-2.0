package com.rayka.smsforwarder

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var dotHeaderStatus: android.view.View

    private val statusPoller = object : Runnable {
        override fun run() {
            val online = NetworkUtils.isOnline(applicationContext)
            dotHeaderStatus.setBackgroundResource(R.drawable.dot_status)
            dotHeaderStatus.background.setTint(
                if (online) resources.getColor(R.color.online_green, theme)
                else resources.getColor(R.color.offline_orange, theme)
            )
            uiHandler.postDelayed(this, 4000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContentView(R.layout.activity_main)

        val imgLogo = findViewById<android.widget.ImageView>(R.id.imgLogo)
        imgLogo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.logo_float))

        dotHeaderStatus = findViewById(R.id.dotHeaderStatus)
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        dotHeaderStatus.startAnimation(pulse)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        viewPager.adapter = PagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "داشبورد"
                1 -> "گزارش دریافتی"
                else -> "شماره‌ها و ارسال به بویه"
            }
        }.attach()
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(statusPoller)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(statusPoller)
    }
}
