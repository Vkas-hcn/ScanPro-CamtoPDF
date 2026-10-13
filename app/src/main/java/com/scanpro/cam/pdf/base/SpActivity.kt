package com.scanpro.cam.pdf.base

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewbinding.ViewBinding
import com.scanpro.cam.pdf.util.SpLocale
import com.scanpro.cam.pdf.util.SpStatusBar

/**
 * Activity 基类：ViewBinding + 沉浸式状态栏。
 * 子类实现 [inflateBinding]，按需覆写 [initView] / [observeData]。
 */
abstract class SpActivity<VB : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: VB
        private set

    /** true：深色图标（浅色背景）；false：浅色图标（深色背景） */
    protected open val lightStatusBarIcons: Boolean = true

    /** true：根布局自动避开状态栏/导航栏；false：内容延伸到系统栏下方，由页面自行处理 */
    protected open val applySystemBarsPadding: Boolean = true

    protected abstract fun inflateBinding(inflater: LayoutInflater): VB

    protected open fun initView(savedInstanceState: Bundle?) {}

    protected open fun observeData() {}

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SpLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = inflateBinding(layoutInflater)
        setContentView(binding.root)
        SpStatusBar.setLightStatusBar(this, lightStatusBarIcons)
        if (applySystemBarsPadding) {
            applySystemBarsInsets(binding.root)
        }
        initView(savedInstanceState)
        observeData()
    }

    protected fun recreateQuietly() {
        overridePendingTransition(0, 0)
        recreate()
        overridePendingTransition(0, 0)
    }

    private fun applySystemBarsInsets(target: View) {
        val initialLeft = target.paddingLeft
        val initialTop = target.paddingTop
        val initialRight = target.paddingRight
        val initialBottom = target.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initialLeft + bars.left,
                top = initialTop + bars.top,
                right = initialRight + bars.right,
                bottom = initialBottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(target)
    }
}
