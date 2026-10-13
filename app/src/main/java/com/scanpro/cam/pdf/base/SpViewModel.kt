package com.scanpro.cam.pdf.base

import androidx.lifecycle.ViewModel

/**
 * ViewModel 基类。业务页面按需继承，后续可在此统一加 Loading / 错误事件等。
 */
open class SpViewModel : ViewModel()
