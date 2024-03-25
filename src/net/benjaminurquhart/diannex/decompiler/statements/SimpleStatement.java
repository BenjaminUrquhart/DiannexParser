package net.benjaminurquhart.diannex.decompiler.statements;

import net.benjaminurquhart.diannex.runtime.Value;

public abstract class SimpleStatement implements Statement {

	public Value first, second;
	protected String modifier;
	
	public SimpleStatement(Value first, Value second, String modifier) {
		this.modifier = modifier;
		this.second = second;
		this.first = first;
	}
	/*
	@Override
	public String toString(int indentation) {
		return toString(indentation, true);
	}*/
	
	public String toString() {
		return String.format("(%s %s %s)", first.get(), modifier, second.get());
	}
}
