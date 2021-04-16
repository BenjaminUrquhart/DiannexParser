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
		T val = pop().get(clazz);
		provider.reclaim();
		return val;
	}
	
	@Override
	public Value push(Value value) {
		
		if(provider.isInUse(value)) {
			value = new Value(value.get());
		}
		
		return super.push(value);
	}

}
