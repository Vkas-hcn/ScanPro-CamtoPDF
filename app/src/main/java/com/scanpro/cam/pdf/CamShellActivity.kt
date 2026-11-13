package com.scanpro.cam.pdf

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.buncead.common.ui.ExcActivity
import com.buncead.common.util.PermNotifyUtil
import com.mymy.mymy.review.ReviewUtil

import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.made.CamMadeFragment
import com.scanpro.cam.pdf.databinding.ActivityCamShellBinding
import com.scanpro.cam.pdf.home.CamHomeFragment
import com.scanpro.cam.pdf.home.FileType
import com.scanpro.cam.pdf.notice.SpNotificationEntry
import com.scanpro.cam.pdf.notice.SpNotifyExtras
import com.scanpro.cam.pdf.main.CamStubFragment
import com.scanpro.cam.pdf.recent.CamRecentFragment
import com.scanpro.cam.pdf.scan.CamCaptureActivity
import com.scanpro.cam.pdf.leave.SpLeaveEntry
import com.scanpro.cam.pdf.leave.SpUnInstall01Activity
import com.scanpro.cam.pdf.util.SpLocale

class CamShellActivity : SpActivity<ActivityCamShellBinding>() {

    override val applySystemBarsPadding: Boolean = false

    private var currentTab = Tab.HOME
    private var pendingNotificationFileType: FileType? = null
    private var appliedLocaleGeneration = SpLocale.generation

    /** 第二处通知权限申请（对齐 luow：启动页问过一次后，主界面再问一次并做二次引导）。 */

    override fun inflateBinding(inflater: LayoutInflater): ActivityCamShellBinding {
        return ActivityCamShellBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        appliedLocaleGeneration = SpLocale.generation
        applyShellNavInset()
        binding.tabHome.setOnClickListener { onBottomTabClicked(Tab.HOME) }
        binding.tabScan.setOnClickListener {
            startActivity(Intent(this, ExcActivity::class.java))
        }
        binding.tabRecent.setOnClickListener { onBottomTabClicked(Tab.RECENT) }
        binding.tabMade.setOnClickListener { onBottomTabClicked(Tab.CREATION) }

        if (savedInstanceState == null) {
            if (!handleNotificationRoute(intent)) switchShellTab(Tab.HOME)
            maybeOpenLeaveFlow(intent)
        } else {
            currentTab = Tab.entries[savedInstanceState.getInt(KEY_TAB, 0)]
            refreshTabChrome(currentTab)
        }

        PermNotifyUtil.check2requestNotificationPermission(mActivity,{
            ReviewUtil().checkReview(mActivity)
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationRoute(intent)
        maybeOpenLeaveFlow(intent)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermNotifyUtil.onRequestPermissionsResult(requestCode, permissions, grantResults, this, { isAllow: Boolean ->
            ReviewUtil().checkReview(mActivity)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        PermNotifyUtil.onActivityResult(requestCode,resultCode,data,this,{ isAllow: Boolean ->
            ReviewUtil().checkReview(mActivity)
        })
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab.ordinal)
    }

    /** Existing notification routes only: no cleaner/file-management business is introduced. */
    private fun handleNotificationRoute(entry: Intent): Boolean {
        if (SpLeaveEntry.isLeaveLaunch(entry)) return false
        val route = SpNotificationEntry.route(entry) ?: return false
        entry.removeExtra(SpNotificationEntry.A_NAV)
        entry.removeExtra(SpNotifyExtras.JUMP_TYPE)
        when (route) {
            "b_recents" -> switchShellTab(Tab.RECENT)
            "b_scan" -> {
                switchShellTab(Tab.HOME)
                binding.root.post {
//                    if (!isFinishing && !isDestroyed) {
//                        SpPageAdTriggers.showFileIntThen(this) {
//                            if (!isFinishing && !isDestroyed) CamCaptureActivity.start(this)
//                        }
//                    }
                }
            }
            "b_home" -> switchShellTab(Tab.HOME)
            else -> {
                val type = when (route) {
                    "WORD" -> FileType.DOC
                    "EXCEL" -> FileType.XLS
                    "PPT" -> FileType.PPT
                    "PDF" -> FileType.PDF
                    else -> return false
                }
                pendingNotificationFileType = type
                switchShellTab(Tab.HOME)
                val home = supportFragmentManager.findFragmentByTag(Tab.HOME.name) as? CamHomeFragment
                if (home?.view != null) {
                    home.selectNotificationFileType(type)
                    pendingNotificationFileType = null
                }
            }
        }
        return true
    }

    fun consumeNotificationFileType(): FileType? = pendingNotificationFileType.also {
        pendingNotificationFileType = null
    }

    private fun maybeOpenLeaveFlow(intent: Intent?) {
        if (!SpLeaveEntry.isLeaveLaunch(intent)) return
        // 标记先摘掉，避免这一页被复用（`onNewIntent` / 重建）时再开一次。
        intent?.removeExtra(SpLeaveEntry.EXTRA_OPEN_LEAVE)
        switchShellTab(Tab.HOME)
        // 进 Apollo 那两步挽留的第 1 步（`UnInstall01Activity`）。
        // 开不开由 `SpLeaveEntry.ENABLED` 和 `res/xml/app_shortcuts.xml` 决定。
        SpUnInstall01Activity.start(this)
    }

    override fun onResume() {
        super.onResume()
        if (appliedLocaleGeneration != SpLocale.generation) {
            appliedLocaleGeneration = SpLocale.generation
            recreateQuietly()
            return
        }
        if (currentTab == Tab.HOME) {
            // 首页直接走 Google Play 应用内评价，不再弹自定义评价框。
            binding.root.post {
                if (!isFinishing && !isDestroyed) {
                }
            }
        }
    }





    private fun applyShellNavInset() {
        val nav = binding.shellNav
        val initialBottom = nav.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.shellRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            nav.updatePadding(bottom = initialBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.shellRoot)
    }

    private fun onBottomTabClicked(tab: Tab) {
        if (currentTab == tab) return
        switchShellTab(tab)
    }



    private fun switchShellTab(tab: Tab) {
        currentTab = tab
        refreshTabChrome(tab)

        val tag = tab.name
        val existing = supportFragmentManager.findFragmentByTag(tag)
        supportFragmentManager.commit {
            supportFragmentManager.fragments.forEach { hide(it) }
            if (existing != null) {
                show(existing)
            } else {
                add(R.id.shell_container, buildTabFragment(tab), tag)
            }
        }
    }

    private fun buildTabFragment(tab: Tab): Fragment {
        return when (tab) {
            Tab.HOME -> CamHomeFragment()
            Tab.SCAN -> CamStubFragment.newInstance(getString(R.string.placeholder_scan))
            Tab.RECENT -> CamRecentFragment()
            Tab.CREATION -> CamMadeFragment()
        }
    }

    private fun refreshTabChrome(selected: Tab) {
        applyTabSelected(binding.tabHome, selected == Tab.HOME)
        applyTabSelected(binding.tabScan, selected == Tab.SCAN)
        applyTabSelected(binding.tabRecent, selected == Tab.RECENT)
        applyTabSelected(binding.tabMade, selected == Tab.CREATION)
    }

    private fun applyTabSelected(tab: LinearLayout, selected: Boolean) {
        tab.isSelected = selected
        for (i in 0 until tab.childCount) {
            when (val child = tab.getChildAt(i)) {
                is ImageView -> child.isSelected = selected
                is TextView -> child.isSelected = selected
                is ViewGroup -> {
                    child.isSelected = selected
                    for (j in 0 until child.childCount) {
                        child.getChildAt(j).isSelected = selected
                    }
                }
                else -> child.isSelected = selected
            }
        }
    }

    private enum class Tab {
        HOME, SCAN, RECENT, CREATION
    }

    companion object {
        private const val KEY_TAB = "key_tab"
    }
}
