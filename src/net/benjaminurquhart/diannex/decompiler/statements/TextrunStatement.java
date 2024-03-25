package net.benjaminurquhart.diannex.decompiler.statements;

public record TextrunStatement(String text) implements Statement {

	public String toString() {
		return text;
	}
}
