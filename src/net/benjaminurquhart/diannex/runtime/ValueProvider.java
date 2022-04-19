package net.benjaminurquhart.diannex.runtime;

/*
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
*/

// This was originally going to be a cool caching thing
// but I messed it up somewhere.
public class ValueProvider {
	
	/*
	private Set<Value> inUse;
	private Stack<Value> values;
	
	public ValueProvider() {
		values = new Stack<>();
		for(int i = 0; i < 10; i++) {
			values.add(new Value(null));
		}
		inUse = new HashSet<>();
	}
	*/
	public Value get(Object obj) {
		if(obj instanceof Value) {
			return ((Value)obj).clone();
		}
		return new Value(obj);
		/*
		if(values.isEmpty()) {
			return new Value(obj);
		}
		Value value = values.pop();
		value.update(obj);
		inUse.add(value);
		return value;*/
	}
	
	public void put(Value value) {/*
		inUse.remove(value);
		values.push(value);*/
	}
	
	public boolean isInUse(Value value) {
		return false; //inUse.contains(value);
	}
	
	public void reclaim() {/*
		for(Value value : inUse) {
			value.update(null);
			values.add(value);
		}
		inUse.clear();*/
	}
}
