package net.benjaminurquhart.diannex.decompiler.statements.operations;

import net.benjaminurquhart.diannex.decompiler.statements.SimpleStatement;
import net.benjaminurquhart.diannex.runtime.Value;

public class BitAndStatement extends SimpleStatement {

	public BitAndStatement(Value first, Value second) {
		super(first, second, "&");
	}

}
