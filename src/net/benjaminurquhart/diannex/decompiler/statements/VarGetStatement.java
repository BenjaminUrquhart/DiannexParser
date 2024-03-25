package net.benjaminurquhart.diannex.decompiler.statements;

public record VarGetStatement(String var) implements Statement {
	
	public String toString() {
		return var;
	}
}
