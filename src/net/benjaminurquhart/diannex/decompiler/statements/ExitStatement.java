package net.benjaminurquhart.diannex.decompiler.statements;

public record ExitStatement() implements Statement {

	public String toString() {
		return "return;";
	}
}
