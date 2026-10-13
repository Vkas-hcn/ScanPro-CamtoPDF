package com.scanpro.cam.pdf.leave

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.boot.SpBootActivity
import com.scanpro.cam.pdf.databinding.ActivitySpBootBinding

/**
 * 桌面「卸载」快捷方式的落点，只是中转一层。等价 Apollo `ui/delete/DeleteActivity`。
 *
 * 为什么要有这一层：静态快捷方式的 intent 不能带 flag，而开屏 [SpBootActivity] 要求
 * 自己必须是 task root（Apollo `SplashActivity` 的 `isTaskRoot` 判断，本工程同款），
 * 否则会直接 `finish()`——App 已在后台时点快捷方式就会什么都不发生。
 * 所以由本页把快捷方式带进来的 action / extras 原样搬到去开屏的 intent 上，
 * 补 `NEW_TASK | CLEAR_TASK`（Apollo 的 `268468224` 就是这两位）再拉起，
 * 清掉旧任务栈，让开屏成为新的 task root。
 *
 * 这一步是启动路径上的中转，不做任何挽留判断；判据在 [SpLeaveEntry]。
 * 界面沿用开屏那套布局：本页只活一帧，跟开屏同屏，切过去不闪。
 */
class SpLeaveEntryActivity : SpActivity<ActivitySpBootBinding>() {

    override val applySystemBarsPadding: Boolean = false

    override fun inflateBinding(inflater: LayoutInflater): ActivitySpBootBinding {
        return ActivitySpBootBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        toBoot(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        toBoot(intent)
    }

    /** Apollo `DeleteActivity.toSplashPage`：搬 extras / action / data / categories，补齐 flag。 */
    private fun toBoot(from: Intent?) {
        val next = Intent(this, SpBootActivity::class.java)
        if (from != null) {
            from.extras?.let { next.putExtras(it) }
            from.action?.let { next.action = it }
            from.data?.let { next.data = it }
            from.categories?.forEach { next.addCategory(it) }
        }
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(next)
        finish()
    }
}
