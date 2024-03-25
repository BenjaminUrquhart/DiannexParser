package net.benjaminurquhart.diannex.decompiler.statements.compare;

import net.benjaminurquhart.diannex.decompiler.statements.SimpleStatement;
import net.benjaminurquhart.diannex.runtime.Value;

public class LessThanStatement extends SimpleStatement {

	public LessThanStatement(Value first, Value second) {
		super(first, second, "<");
	}

}
