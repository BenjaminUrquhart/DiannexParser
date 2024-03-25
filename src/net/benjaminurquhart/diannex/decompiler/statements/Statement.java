package net.benjaminurquhart.diannex.decompiler.statements;

public interface Statement {

	public default String toString(int indentation) {
		return toString(indentation, false);
	}
	
	public default String toString(int indentation, boolean wrap) {
		if(wrap) {
			return "\t".repeat(indentation) + "(" + toString() + ")";
		}
		return "\t".repeat(indentation) + toString();
	}
}
