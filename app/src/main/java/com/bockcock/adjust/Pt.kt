package com.bockcock.adjust

import android.os.Bundle

class Pt {
    val map: MutableMap<String, String> = mutableMapOf()
    val bundle: Bundle = Bundle()

    public constructor()

    constructor(name: String, value: String){
        map.put(name,value)
        bundle.putString(name,value)
    }

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

    override fun toString(): String {
        return map.toString()
    }
}