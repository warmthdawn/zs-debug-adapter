package com.warmthdawm.zsdebugadapter.jdi.variable


interface VariableTreeNode {
    val name: String
    val children: List<JDIVariable>?
        get() = null
}
