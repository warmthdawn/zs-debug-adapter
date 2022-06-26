package com.warmthdawm.zsdebugadapter.jdi.variable

import com.sun.jdi.*

class JDIVariable(
    override val name: String,
    private val jdiValue: Value?,
    jdiType: Type? = null
) : VariableTreeNode {

    val value: String = jdiValue?.toString() ?: "null"
    val type: String = (jdiType?.name() ?: jdiValue?.type()?.name()) ?: "Unknown type"
    override val children: List<JDIVariable>? by lazy { jdiValue?.let(::childrenOf) }
    val id: Long? = (jdiValue as? ObjectReference)?.uniqueID() ?: (jdiValue as? ArrayReference)?.uniqueID()

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
        .map { JDIVariable(it.name(), jdiValue.getValue(it), jdiType) }

}

fun localVariable(name: String, jdiValue: Value, jdiType: Type? = null) = JDIVariable(name, jdiValue, jdiType)
fun thisVariable(thisObj: ObjectReference?) = thisObj?.let { JDIVariable("this", it) }

fun computedVariable(jdiValue: Value?) = jdiValue?.let { JDIVariable("computed", it) }