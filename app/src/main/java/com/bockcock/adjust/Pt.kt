package com.bockcock.adjust

import android.os.Bundle

class Pt {
    val map: MutableMap<String, String> = mutableMapOf()
    val bundle: Bundle = Bundle()
    // Compatibility with B's previous merged Pt; A's bundle remains the canonical data.
    val params: Bundle get() = bundle

    public constructor()

    constructor(name: String, value: String){
        map.put(name,value)
        bundle.putString(name,value)
    }

    // B previously accepted Any in the constructor. Keep it without losing numeric types.
    constructor(name: String, value: Any) { put(name, value) }

    fun put(name: String,value: String): Pt {
        map.put(name,value)
        bundle.putString(name,value)
        return this
    }
    fun put(name: String,value: Double): Pt {
        map.put(name,"$value")
        bundle.putDouble(name,value)
        return this
    }
    fun put(name: String,value: Long): Pt {
        map.put(name,"$value")
        bundle.putLong(name,value)
        return this
    }

    fun put(name: String, value: Any): Pt {
        when (value) {
            is String -> return put(name, value)
            is Double -> return put(name, value)
            is Long -> return put(name, value)
            is Int -> bundle.putInt(name, value)
            is Boolean -> bundle.putBoolean(name, value)
            is Float -> bundle.putFloat(name, value)
            else -> return put(name, value.toString())
        }
        map[name] = value.toString()
        return this
    }

    override fun toString(): String {
        return map.toString()
    }
}
