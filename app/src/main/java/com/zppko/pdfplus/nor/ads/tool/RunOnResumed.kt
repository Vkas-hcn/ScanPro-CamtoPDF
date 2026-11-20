package com.zppko.pdfplus.nor.ads.tool

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.buncead.common.ui.BaseActivity

/**
 * 在宿主 Activity 可见（RESUMED）后执行 block；若已经 RESUMED 则立即执行。
 *
 * 广告关闭回调到达时，onResume 可能已经发生过（onResume 是一次性事件），
 * 此时再注册 observer 会永远等不到下一次 onResume，导致页面卡住。
 *
 * 统一在主线程执行；未 RESUMED 时保留 observer，Activity 销毁时移除。
 */
object RunOnResumed {

    fun run(act: BaseActivity, whenVisible: Boolean = true, block: () -> Unit) {
        if (!whenVisible) {
            act.runOnUiThread { block.invoke() }
            return
        }
        act.runOnUiThread {
            val state = act.lifecycle.currentState
            if (state == Lifecycle.State.DESTROYED) {
                return@runOnUiThread
            }
            if (state.isAtLeast(Lifecycle.State.RESUMED)) {
                block.invoke()
                return@runOnUiThread
            }
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    owner.lifecycle.removeObserver(this)
                    block.invoke()
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    owner.lifecycle.removeObserver(this)
                }
            }
            act.lifecycle.addObserver(observer)
        }
    }
}
