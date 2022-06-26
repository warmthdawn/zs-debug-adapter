package com.warmthdawm.zsdebugadapter.jdi.variable;

data class VariableScope(
    override val name: String,
    override val children: List<JDIVariable>
) : VariableTreeNode
