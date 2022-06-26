package com.warmthdawm.zsdebugadapter.util

class ZDAException : RuntimeException {
    constructor(msg: String) : super(msg)

    constructor(msg: String, cause: Throwable) : super(msg, cause)
}