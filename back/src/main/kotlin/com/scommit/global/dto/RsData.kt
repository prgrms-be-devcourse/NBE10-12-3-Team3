package com.scommit.global.dto

import com.fasterxml.jackson.annotation.JsonIgnore

data class RsData<T>(
    val resultCode: String,
    @JsonIgnore
    val statusCode: Int,
    val msg: String,
    val data: T?,
) {
    fun statusCode() = statusCode

    @JvmOverloads
    constructor(resultCode: String, msg: String, data: T? = null) : this(
        resultCode,
        resultCode.substringBefore('-').toInt(),
        msg,
        data,
    )
}
