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
		return value != null && (value instanceof Number);
	}
	
	public void update(Object obj) {
		castCache.clear();
		if(obj instanceof Boolean) {
			value = (long)(((boolean)obj) ? 1 : 0);
		}
		else if(obj != null && obj.getClass().isArray() && !Object[].class.isInstance(obj)) {
			throw new IllegalArgumentException("Primitive arrays are not supported");
		}
		else if(obj instanceof Integer) {
			value = ((Integer)obj).longValue();
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
		Class<?> valClazz = value.getClass();
		if(clazz == Object.class) {
			//System.out.printf("%sGet '%s' (%s) as raw object%s\n", ANSI.GRAY, value, value == null ? null : value.getClass(), ANSI.RESET);
			return (T)value;
		}
		//System.out.printf("%sCast '%s' (%s) to %s%s\n", ANSI.GRAY, value, value == null ? null : value.getClass(), clazz, ANSI.RESET);
		if(clazz.isArray()) {
			if(valClazz.isArray()) {
				if(clazz.isInstance(value)) {
					return (T)value;
				}
				throw new ClassCastException("Cannot cast to an array of primitive type");
			}
			else {
				throw new IllegalStateException("Attempting to read non-array as array");
			}
		}
		if(clazz == boolean.class || clazz == Boolean.class) {
			boolean ret = true;
			if(this.isFloatingPoint()) {
				ret = Math.round(get(double.class)) != 0;
			}
			else if(this.containsNumber()) {
				ret = ((Number)value).longValue() > 0;
			}
			else if(value instanceof String) {
				ret = !((String)value).isEmpty();
			}
			return (T)Boolean.valueOf(ret);
		}
		// Don't ask
		if(clazz.isPrimitive() && (value instanceof Number) && valClazz.getName().startsWith("java.lang") && ((valClazz == Integer.class && clazz == int.class) || value.getClass().getSimpleName().equalsIgnoreCase(clazz.getName()))) {
			return (T)value;
		}
		if(clazz == String.class) {
			// Implementation-dependent: the TS!Underswap Diannex runtime
			// in particular will error when trying to read arrays as strings.
			// This is more for debugging and shouldn't be relied upon.
			if(valClazz.isArray()) {
				return (T)Arrays.deepToString((Object[])value);
			}
			return (T)String.valueOf(value);
		}
		Throwable cause = null;
		try {
			Method getter = null;
			if(value instanceof Number) {
				// Slow, but I value my sanity more
				getter = numGetterFor(clazz);
				if(getter != null) {
					//System.out.printf("%sCast '%s' (%s) to %s via %s%s\n", ANSI.GRAY, value, value == null ? null : value.getClass(), clazz, getter, ANSI.RESET);
					return (T)getter.invoke(value);
				}
			}
			else {
				// I might not keep this in since it might be going a bit too far 
				// with type coercion. What this does basically is turn the value into
				// a string and try to parse the string value into the requested object.
				//
				// Right now it's only used to turn strings containing ints into a 
				// numerical type (technically it also parses doubles, but the runtime
				// doesn't handle it) meaning you can do dumb stuff like "2" - 1.
				if(clazz.isPrimitive()) {
					Class<? extends Number> realClazz;
					if(clazz == int.class) {
						realClazz = Integer.class;
					}
					else {
						String name = clazz.getName();
						if(name.length() == 1) {
							name = name.toUpperCase();
						}
						else {
							name = name.substring(0, 1).toUpperCase() + name.substring(1);
						}
						realClazz = (Class<? extends Number>)Class.forName("java.lang." + name);
					}
					getter = realClazz.getMethod("valueOf", String.class);
				}
				else {
					getter = clazz.getMethod("valueOf", String.class);
				}
				return (T)getter.invoke(null, String.valueOf(value));
			}
		}
		catch(IllegalAccessException | NoSuchMethodException | ClassNotFoundException e) {
			cause = e;
		}
		catch(InvocationTargetException e) {
			cause = e.getCause();
		}
		catch(RuntimeException e) {
			throw e;
		}
		ClassCastException e = new ClassCastException(valClazz.getName() + " cannot be cast to " + clazz.getName());
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
