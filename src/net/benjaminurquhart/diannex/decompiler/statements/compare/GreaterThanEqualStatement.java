package net.benjaminurquhart.diannex.decompiler.statements.compare;

import net.benjaminurquhart.diannex.decompiler.statements.SimpleStatement;
import net.benjaminurquhart.diannex.runtime.Value;

public class GreaterThanEqualStatement extends SimpleStatement {

	public GreaterThanEqualStatement(Value first, Value second) {
		super(first, second, ">=");
	}

}
