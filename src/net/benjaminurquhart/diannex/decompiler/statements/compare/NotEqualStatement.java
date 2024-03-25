package net.benjaminurquhart.diannex.decompiler.statements.compare;

import net.benjaminurquhart.diannex.decompiler.statements.SimpleStatement;
import net.benjaminurquhart.diannex.runtime.Value;

public class NotEqualStatement extends SimpleStatement {

	public NotEqualStatement(Value first, Value second) {
		super(first, second, "!=");
	}

}
