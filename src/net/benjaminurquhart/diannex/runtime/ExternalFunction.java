package net.benjaminurquhart.diannex.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class ExternalFunction {
	
	
	public static Set<ExternalFunction> getFrom(Object object) {
		return getFunctionList(object.getClass(), object);
	}
	
	public static Set<ExternalFunction> getFrom(Class<?> clazz) {
		return getFunctionList(clazz, null);
	}
	
	private static Set<ExternalFunction> getFunctionList(Class<?> clazz, Object instance) {
		Set<ExternalFunction> out = new HashSet<>();
		Set<Method> seen = new HashSet<>();
		
		Consumer<Method> addConditional = method -> {
			if(seen.add(method) && method.isAnnotationPresent(ExternalDNXFunction.class)) {
				String name = method.getAnnotation(ExternalDNXFunction.class).value();
				ExternalFunction func;
				if(name.isBlank()) {
					name = method.getName();
				}
				
				if(Modifier.isStatic(method.getModifiers())) {
					func = new ExternalFunction(null, method);
				}
				else if(method.canAccess(instance)) {
					func = new ExternalFunction(instance, method);
				}
				else {
					System.err.println("Warning: could not access annotated method " + method);
					return;
				}
				func.name = name;
				out.add(func);
			}
		};
		
		for(Method method : clazz.getMethods()) {
			addConditional.accept(method);
		}
		for(Method method : clazz.getDeclaredMethods()) {
			addConditional.accept(method);
		}
		return out;
	}
	
	
	

	private ValueProvider provider;
	
	private Function<Object, ?> lambdaFunction;
	private Object callingInstance;
	private Method function;
	private String name;
	
	private boolean takesContext;
	
	protected ExternalFunction() {
		this.name = "unnamedFunction" + hashCode();
	}
	
	
	public ExternalFunction(Consumer<?> function) {
		this(function, "lambda" + function.hashCode());
	}
	
	@SuppressWarnings("unchecked")
	public ExternalFunction(Consumer<?> function, String name) {
		this(obj -> {
			((Consumer<Object>)function).accept(obj);
			return null;
		}, name);
	}
	
	public ExternalFunction(Function<?, ?> function) {
		this(function, "lambda" + function.hashCode());
	}
	
	@SuppressWarnings("unchecked")
	public ExternalFunction(Function<?, ?> function, String name) {
		this.lambdaFunction = (Function<Object, ?>)function;
		this.name = name;
	}
	
	public ExternalFunction(Method method) {
		this(null, method);
	}
	
	
	public ExternalFunction(Object object, Method method) {
		this.callingInstance = object;
		this.function = method;
		
		this.takesContext = method.getParameterCount() > 0 && method.getParameterTypes()[0] == RuntimeContext.class;
		this.name = method.getName();
		
		if(!method.canAccess(object)) {
			if(object == null) {
				throw new IllegalArgumentException("Cannot call instance method " + method + " with null object");
			}
			else if(!method.getDeclaringClass().isInstance(object)) {
				throw new IllegalArgumentException("Object of type " + object.getClass().getName() + " cannot access " + method);
			}
			throw new IllegalArgumentException("Cannot call static method " + method + " with non-null object");
		}
	}
	
	public ExternalFunction(Object obj, String name, Class<?>... args) throws NoSuchMethodException {
		this(obj, obj.getClass().getMethod(name, args));
	}
	
	public ExternalFunction(Class<?> clazz, String name, Class<?>... args) throws NoSuchMethodException {
		this(null, clazz.getMethod(name, args));
	}
	
	public void setValueProvider(ValueProvider provider) {
		this.provider = provider;
	}
	
	public Class<?>[] getArgumentTypes() {
		if(lambdaFunction != null) {
			return new Class<?>[]{ Object.class };
		}
		Class<?>[] out = function.getParameterTypes();
		if(takesContext) {
			return Arrays.copyOfRange(out, 1, out.length);
		}
		return out;
	}
	
	public int getArgumentCount() {
		if(lambdaFunction != null) {
			return 1;
		}
		return function.getParameterCount() - (takesContext ? 1 : 0);
	}
	
	public boolean takesContext() {
		return takesContext;
	}
	
	public String getName() {
		return name;
	}
	
	public Value execute(Object... args) {
		if(lambdaFunction != null) {
			if(args.length != 1) {
				throw new IllegalStateException("Lambda function requires 1 parameter, got " + args.length);
			}
			return provider.get(lambdaFunction.apply(args[0]));
		}
		else if(function != null) {
			if(args.length != function.getParameterCount()) {
				throw new IllegalStateException("Expected " + function.getParameterCount() + " argument(s), got " + args.length);
			}
			try {
				return provider.get(function.invoke(callingInstance, args));
			}
			catch(InvocationTargetException e) {
				if(e.getCause() instanceof RuntimeException) {
					throw (RuntimeException)e.getCause();
				}
				throw new RuntimeException(e.getCause());
			}
			catch(IllegalAccessException e) {
				throw new IllegalStateException(function + " is not accessible");
			}
		}
		throw new IllegalStateException("Cannot execute null function");
	}
	
	@Override
	public String toString() {
		if(lambdaFunction != null) {
			return name + "(argc=1)";
		}
		return function.getDeclaringClass().getName() + "::" + name + "(argc=" + function.getParameterCount() + ")";
	}
}
