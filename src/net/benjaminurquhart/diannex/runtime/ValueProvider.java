package net.benjaminurquhart.diannex.runtime;


import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

public class ValueProvider {
	
	
	private Set<Value> inUse;
	private Stack<Value> values;
	
	public ValueProvider() {
		values = new Stack<>();
		for(int i = 0; i < 10; i++) {
			values.add(new Value(null));
		}
		inUse = new HashSet<>();
	}
	
	public Value get(Object obj) {
		while(obj instanceof Value) {
			obj = ((Value)obj).get();
		}
		
		Value value;
		if(values.isEmpty()) {
			value = new Value(obj);
		}
		else {
			value = values.pop();
		}
		value.update(obj);
		inUse.add(value);
		return value;
	}
	
	public void put(Value value) {
		inUse.remove(value);
		values.push(value);
	}
	
	public boolean isInUse(Value value) {
		return inUse.contains(value);
	}
	
	public void reclaim() {
		for(Value value : inUse) {
			value.update(null);
			values.add(value);
		}
		inUse.clear();
	}
}
