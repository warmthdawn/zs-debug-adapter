package com.warmthdawm.zsdebugadapter.adapter

import com.sun.jdi.LocalVariable
import com.sun.jdi.ObjectReference
import com.sun.jdi.PrimitiveType
import com.sun.jdi.PrimitiveValue
import com.sun.jdi.Value
import com.warmthdawm.zsdebugadapter.jdi.JDIDebuggee
import com.warmthdawm.zsdebugadapter.jdi.variable.*
import com.warmthdawm.zsdebugadapter.util.ThreadDataPool
import com.warmthdawm.zsdebugadapter.util.catching
import org.eclipse.lsp4j.debug.Scope
import org.eclipse.lsp4j.debug.ScopePresentationHint
import org.eclipse.lsp4j.debug.ScopesResponse
import org.eclipse.lsp4j.debug.StackFrame as LSPStackFrame
import com.sun.jdi.StackFrame as JDIStackFrame
import org.eclipse.lsp4j.debug.StackTraceResponse
import org.eclipse.lsp4j.debug.Thread as LSPThread
import org.eclipse.lsp4j.debug.Variable
import com.warmthdawm.zsdebugadapter.jdi.JDIThread
import com.warmthdawm.zsdebugadapter.util.SimpleDataPool
import org.eclipse.lsp4j.debug.EvaluateResponse

class JDI2LSPConverter(
    private val debugee: JDIDebuggee,
) {

    private val stackFramePool = ThreadDataPool<JDIStackFrame>()
    private val variablesPool = SimpleDataPool<VariableTreeNode>()

    fun clearDebugCache(thread: Long) {
        stackFramePool.removeAllByThread(thread)
        variablesPool.clear()
    }

    fun convertVariables(variable: JDIVariable) = Variable().apply {
        name = variable.name
        value = variable.value
        type = variable.type

        if (!variable.children.isNullOrEmpty()) {
            variablesReference = variablesPool.put(variable)
        }
    }

    fun convertEvaluateResponse(result: Value?) = EvaluateResponse().apply {
        this.result = result?.toString() ?: "null"
        if(result != null && result !is PrimitiveValue) {
            this.variablesReference = variablesPool.put(computedVariable(result)!!)
        }
    }

    fun convertScopes(frame: JDIStackFrame) = ScopesResponse().apply {

        scopes = buildList {


            val parameters = mutableSetOf<LocalVariable>()
            //parameter
            val arguments = mutableListOf<JDIVariable>()
            catching("Failed to get method arguments of $frame") {
                frame.location().method().arguments().forEach { param ->
                    catching("Failed to get value of $param") {
                        frame.getValue(param)?.let { arguments.add(localVariable(param.name(), it, param.type())) }
                        parameters.add(param)

                    }
                }
            }



            val locals = mutableListOf<JDIVariable>()

            //this
            catching("Failed to get 'this' variable") {
                thisVariable(frame.thisObject())?.let { locals.add(it) }
            }

            //local variable
            catching("Failed to get local variables of $frame") {
                frame.visibleVariables().forEach { variable ->
                    if(variable !in parameters) {
                        catching("Failed to get value of $variable") {
                            frame.getValue(variable)
                                ?.let { locals.add(localVariable(variable.name(), it, variable.type())) }
                        }
                    }
                }
            }

            val fields = mutableListOf<JDIVariable>()

            //field
            catching("Failed to get field of $frame") {
                val declaringClazz = frame.location().declaringType()
                declaringClazz.allFields().forEach { field ->
                    catching("Failed to get value of $field") {
                        declaringClazz.getValue(field)?.let { fields.add(localVariable(field.name(), it, field.type())) }
                    }
                }
            }



            if (locals.isNotEmpty()) {
                add(Scope().apply {
                    name = "Locals"
                    presentationHint = ScopePresentationHint.LOCALS
                    variablesReference = variablesPool.put(VariableScope("Locals", locals))
                })
            }


            if (arguments.isNotEmpty()) {
                add(Scope().apply {
                    name = "Arguments"
                    presentationHint = ScopePresentationHint.ARGUMENTS
                    variablesReference = variablesPool.put(VariableScope("Arguments", arguments))
                })
            }

            if (fields.isNotEmpty()) {
                add(Scope().apply {
                    name = "Arguments"
                    presentationHint = ScopePresentationHint.ARGUMENTS
                    variablesReference = variablesPool.put(VariableScope("Arguments", arguments))
                })
            }


        }.toTypedArray()

    }


    fun getStackFrame(id: Int) = stackFramePool.getById(id)
    fun getVariableTree(id: Int) = variablesPool.getById(id)

    fun convertStackTrace(frames: List<JDIStackFrame>) = StackTraceResponse().apply {
        totalFrames = frames.size
        stackFrames = frames.mapNotNull {
            catching("Failed to convert stack frame $it") {
                LSPStackFrame().apply {
                    id = stackFramePool.put(it.thread().uniqueID(), it)
                    val location = it.location();
                    name = location.method()?.name() ?: "Unknown"
                    line = location.lineNumber()
                    column = 1
                    source = debugee.sourceOf(location)
                }
            }.getOrNull()
        }.toTypedArray()

    }

    fun convertThread(id: Long, thread: JDIThread) = LSPThread().apply {
        this.id = id.toInt()
        name = thread.name
    }


}
