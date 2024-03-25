package net.benjaminurquhart.diannex.decompiler.statements.compare;

import net.benjaminurquhart.diannex.decompiler.statements.SimpleStatement;
import net.benjaminurquhart.diannex.runtime.Value;

public class GreaterThanStatement extends SimpleStatement {

	public GreaterThanStatement(Value first, Value second) {
		super(first, second, ">");
	}

}
