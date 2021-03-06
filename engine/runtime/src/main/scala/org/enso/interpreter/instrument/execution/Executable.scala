package org.enso.interpreter.instrument.execution

import org.enso.interpreter.instrument.InstrumentFrame
import org.enso.polyglot.runtime.Runtime.Api

import scala.collection.mutable

/**
  * Represents executable piece of enso program.
  *
  * @param contextId an identifier of a context to execute
  * @param stack a call stack that must be executed
  * @param updatedVisualisations a list of updated visualisations
  */
case class Executable(
  contextId: Api.ContextId,
  stack: mutable.Stack[InstrumentFrame],
  updatedVisualisations: Seq[Api.ExpressionId]
)
