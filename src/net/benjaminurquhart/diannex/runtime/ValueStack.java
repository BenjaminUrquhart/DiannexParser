package net.benjaminurquhart.diannex.runtime;

import java.util.Stack;

public class ValueStack extends Stack<Value> {
	
	private ValueProvider provider;
	
	public ValueStack() {
		this(new ValueProvider());
	}
	
	public ValueStack(ValueProvider provider) {
		this.provider = provider;
	}
	
	private static final long serialVersionUID = -1176254484320921997L;
	
	public void pushObj(Object obj) {
		push(provider.get(obj));
	}
	
	public <T> T peek(Class<T> clazz) {
		return peek().get(clazz);
	}
	
	public <T> T pop(Class<T> clazz) {
		Value value = pop();
		T val = value.get(clazz);
		provider.put(value);
		return val;
	}
	
	@Override
	public Value pop() {
		if(isEmpty()) {
			System.err.printf("%sStack empty, returning null\n", ANSI.RESET);
			return Value.NULL;
		}
		return super.pop();
	}
	
	@Override
	public Value push(Value value) {
		if(value == null) {
			value = Value.NULL;
		}
		
		return super.push(value);
	}

}
