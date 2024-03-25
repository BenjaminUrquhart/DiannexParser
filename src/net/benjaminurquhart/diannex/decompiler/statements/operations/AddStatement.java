package net.benjaminurquhart.diannex.decompiler.statements.operations;

import net.benjaminurquhart.diannex.decompiler.statements.SimpleStatement;
import net.benjaminurquhart.diannex.runtime.Value;

public class AddStatement extends SimpleStatement {

	public AddStatement(Value first, Value second) {
		super(first, second, "+");
	}

}
