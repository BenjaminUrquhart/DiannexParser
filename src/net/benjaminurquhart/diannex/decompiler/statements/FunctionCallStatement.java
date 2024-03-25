package net.benjaminurquhart.diannex.decompiler.statements;

import java.util.Arrays;
import java.util.stream.Collectors;

import net.benjaminurquhart.diannex.runtime.Value;

public class FunctionCallStatement implements Statement {

	public String target;
	public Value[] args;
	
	public FunctionCallStatement(String target, Value... args) {
		this.target = target;
		this.args = args;
	}
	
	public String toString() {
		return String.format("%s(%s)", target, Arrays.stream(args)
													 .map(Value::get)
													 .map(String::valueOf)
													 .collect(Collectors.joining(", ")));
	}
}
