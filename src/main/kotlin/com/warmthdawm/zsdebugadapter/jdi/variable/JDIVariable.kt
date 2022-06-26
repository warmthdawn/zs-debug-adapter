package com.warmthdawm.zsdebugadapter.jdi.variable

import com.sun.jdi.*

class JDIVariable(
    override val name: String,
    private val jdiValue: Value?,
) : VariableTreeNode {

    val value: String = jdiValue?.toString() ?: "null"
    val type: String by lazy { jdiValue?.type()?.name() ?: "Unknown Type" }
    override val children: List<JDIVariable>? by lazy { jdiValue?.let(::childrenOf) }

    private fun childrenOf(jdiValue: Value): List<JDIVariable> {
        return when (val jdiType = jdiValue.type()) {
            is ReferenceType -> when (jdiType) {
                is ArrayType -> arrayElementsOf(jdiValue as ArrayReference)
                else -> fieldsOf(jdiValue as ObjectReference, jdiType)
            }
            else -> emptyList()
        }
    }

    private fun arrayElementsOf(jdiValue: ArrayReference): List<JDIVariable> = jdiValue.values
        .mapIndexed { i, it -> JDIVariable(i.toString(), it) }

    private fun fieldsOf(jdiValue: ObjectReference, jdiType: ReferenceType) = jdiType.allFields()
        .map { JDIVariable(it.name(), jdiValue.getValue(it)) }

}

fun localVariable(name: String, jdiValue: Value) = JDIVariable(name, jdiValue)
fun thisVariable(thisObj: ObjectReference?) = thisObj?.let { JDIVariable("this", it) }

fun computedVariable(jdiValue: Value?) = jdiValue?.let { JDIVariable("computed", it) }