package org.enso.interpreter.node.function;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.Function;

public class CreateFunctionNode extends ExpressionNode {
  private final RootCallTarget callTarget;

  public CreateFunctionNode(RootCallTarget callTarget) {
    this.callTarget = callTarget;
  }

  @Override
  public void markTail() {
    ((EnsoRootNode) callTarget.getRootNode()).markTail();
  }

  @Override
  public Function executeFunction(VirtualFrame frame) {
    MaterializedFrame scope = frame.materialize();
    return new Function(callTarget, scope);
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return executeFunction(frame);
  }
}
