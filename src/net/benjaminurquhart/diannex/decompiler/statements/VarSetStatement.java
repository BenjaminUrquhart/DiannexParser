package net.benjaminurquhart.diannex.decompiler.statements;

import net.benjaminurquhart.diannex.runtime.Value;

public record VarSetStatement(String var, Value value) implements Statement {
	
	public String toString() {
		if(var.startsWith("$")) {
			return String.format("%s = %s", var, value.get());
		}
		return String.format("$%s = %s", var, value.get());
	}
}
