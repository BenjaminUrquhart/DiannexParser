package net.benjaminurquhart.diannex.decompiler.statements.operations;

import net.benjaminurquhart.diannex.decompiler.statements.SimpleStatement;
import net.benjaminurquhart.diannex.runtime.Value;

public class DivideStatement extends SimpleStatement {

	public DivideStatement(Value first, Value second) {
		super(first, second, "/");
	}

}
