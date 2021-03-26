package net.benjaminurquhart.diannex;

public class DNXInterpreter {

	private static class Variable {
		
		static enum Type {
			STRING,
			ARRAY,
			REAL
		}
		
		protected Object value;
		protected Type type;
	}
}
