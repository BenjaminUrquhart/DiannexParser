package net.benjaminurquhart.diannex.decompiler.statements.operations;

import net.benjaminurquhart.diannex.decompiler.statements.Statement;
import net.benjaminurquhart.diannex.runtime.Value;

public record InvertStatement(Value inv) implements Statement {

	public String toString() {
		return "!" + inv.get();
	}
}
