package net.benjaminurquhart.diannex.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Value {
	
	private static Map<Class<?>, Method> numGetters = new HashMap<>();
	
	private Map<Class<?>, Object> castCache;
	private Object value;
	
	public Value(Object value) {
		castCache = Collections.synchronizedMap(new HashMap<>());
		update(value);
	}
	
	public boolean isFloatingPoint() {
		if(value == null) {
			return false;
		}
		return value.getClass() == float.class || value.getClass() == Float.class || value.getClass() == double.class || value.getClass() == Double.class;
	}
	
	public boolean containsNumber() {
		return value != null && numGetterFor(value.getClass()) != null;
	}
	
	public void update(Object obj) {
		castCache.clear();
		if(obj instanceof Boolean) {
			value = ((boolean)obj) ? 1 : 0;
		}
		else if(obj != null && obj.getClass().isArray() && !Object[].class.isInstance(obj)) {
			throw new IllegalArgumentException("Primative arrays are not supported");
		}
 		else {
			value = obj;
		}
	}
	
	private static Method numGetterFor(Class<?> clazz) {
		return numGetters.computeIfAbsent(clazz, c -> {
			try {
				String type = null;
				if(clazz == Integer.class) {
					type = "int";
				}
				else {
					type = clazz.getSimpleName().toLowerCase();
				}
				return Number.class.getMethod(type + "Value");
			}
			catch(NoSuchMethodException e) {
				return null;
			}
			catch(RuntimeException e) {
				throw e;
			}
			catch(Throwable e) {
				throw new RuntimeException(e);
			}
		});
	}
	
	public Object get() {
		return value;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T get(Class<T> clazz) {
		if(!castCache.containsKey(clazz)) {
			castCache.put(clazz, getInternal(clazz));
		}
		return (T)castCache.get(clazz);
	}
	
	@SuppressWarnings("unchecked")
	private <T> T getInternal(Class<T> clazz) {
		if(value == null || value.getClass() == clazz) {
			//System.out.printf("%sGet '%s' (%s)%s\n", ANSI.GRAY, value, value == null ? null : value.getClass(), ANSI.RESET);
			return clazz.cast(value);
		}
		if(clazz == Object.class) {
			//System.out.printf("%sGet '%s' (%s) as raw object%s\n", ANSI.GRAY, value, value == null ? null : value.getClass(), ANSI.RESET);
			return (T)value;
		}
		//System.out.printf("%sCast '%s' (%s) to %s%s\n", ANSI.GRAY, value, value == null ? null : value.getClass(), clazz, ANSI.RESET);
		if(clazz.isArray()) {
			if(value.getClass().isArray()) {
				if(clazz.isInstance(value)) {
					return (T)value;
				}
				throw new ClassCastException("Cannot cast to an array of primative type");
			}
			else {
				throw new IllegalStateException("Attempting to read non-array as array");
			}
		}
		if(clazz == boolean.class || clazz == Boolean.class) {
			return (T)(Boolean.valueOf(value != null && get(int.class) > 0));
		}
		if(clazz == String.class) {
			// Implementation-dependent: the TS!Underswap Diannex runtime
			// in particular will error when trying to read arrays as strings.
			// This is more for debugging and shouldn't be relied upon.
			if(value.getClass().isArray()) {
				return (T)Arrays.deepToString((Object[])value);
			}
			return (T)String.valueOf(value);
		}
		Throwable cause = null;
		try {
			// Slow, but I value my sanity more
			Method getter = numGetterFor(clazz);
			if(getter != null) {
				return (T)getter.invoke(value);
			}
		}
		catch(InvocationTargetException | IllegalAccessException e) {
			cause = e;
		}
		catch(RuntimeException e) {
			throw e;
		}
		ClassCastException e = new ClassCastException(value.getClass().getName() + " cannot be cast to " + clazz.getName());
		try {
			e.initCause(cause);
		}
		catch(IllegalStateException ex) {}
		
		throw e;
	}
	
	public Value clone() {
		return new Value(value);
	}
	
	@Override
	public String toString() {
		return String.valueOf(value);
	}
}
