package com.warmthdawm.zsdebugadapter.jdi.eval

import com.sun.jdi.*
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException


val callExpression = "^(\\w+)\\((.*)\\)$".toRegex()
val str = "^\"(.*)\"$".toRegex()

class SimpleEvaluator(
    val vm: VirtualMachine,
    val stackFrame: StackFrame,
) {


    fun Value.evalMember(member: String): Value? {
        val type = type() as? ObjectReference ?: return null
        return type.getValue(type.referenceType().fieldByName(member))
    }


    fun Value.evalArray(index: Int): Value? {
        val type = type() as? ArrayReference ?: return null
        return type.getValue(index)
    }

    fun Value.evalMethod(name: String, args: List<Value>): Value? {
        val type = type() as? ObjectReference ?: return null

        for (method in type.referenceType().allMethods()) {
            if (method.name() != name)
                continue

            val params = method.argumentTypes()
            if (params.size != args.size && !(method.isVarArgs && params.size < args.size))
                continue
            try {
                return type.invokeMethod(stackFrame.thread(), method, args, ObjectReference.INVOKE_SINGLE_THREADED)
            } catch (_: InvalidTypeException) {
            }

        }
        return null
    }



}