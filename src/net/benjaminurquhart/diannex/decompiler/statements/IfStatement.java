package net.benjaminurquhart.diannex.decompiler.statements;

import net.benjaminurquhart.diannex.runtime.Value;

public record IfStatement(Value condition) implements Statement {

	public String toString() {
		return String.format("if %s", condition.get()) ;
	}
}
