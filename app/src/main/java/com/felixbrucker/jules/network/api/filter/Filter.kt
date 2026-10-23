package com.felixbrucker.jules.network.api.filter

sealed interface Filter<out T> {
    fun toAip160String(): String

    data class And<out T>(val left: Filter<T>, val right: Filter<T>) : Filter<T> {
        override fun toAip160String(): String = "${left.toAip160String()} AND ${right.toAip160String()}"
    }

    data class Or<out T>(val left: Filter<T>, val right: Filter<T>) : Filter<T> {
        override fun toAip160String(): String = "${left.toAip160String()} OR ${right.toAip160String()}"
    }
}

infix fun <T> Filter<T>.and(other: Filter<T>): Filter<T> = Filter.And(this, other)
infix fun <T> Filter<T>.or(other: Filter<T>): Filter<T> = Filter.Or(this, other)
