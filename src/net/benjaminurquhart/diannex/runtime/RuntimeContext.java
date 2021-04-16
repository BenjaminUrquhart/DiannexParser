package net.benjaminurquhart.diannex.runtime;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import net.benjaminurquhart.diannex.DNXFile;

public class RuntimeContext {
	
	private static final BiFunction<String, Object[], ?> defaultMissingFunctionHandler = (name, args) -> {
		throw new IllegalArgumentException("Undefined external function: " + name);
	};
	
	private BiFunction<String, Object[], ?> missingFunctionHandler = defaultMissingFunctionHandler;
	private Map<String, ExternalFunction> externalFunctions = new HashMap<>();
	
	protected String typer = "Narrator";
	
	public Map<Integer, Value> localVars = new HashMap<>();
	public Map<String, Value> globalVars = new HashMap<>();
	public ValueProvider provider = new ValueProvider();
	
	public ValueStack stack = new ValueStack(provider);
	public Value[] working = new Value[3];
	public DNXFile file;
	
	protected Choicer choicer;
	
	protected boolean choiceBeg, didTextRun, choiced;
	protected int ptr;
	
	public RuntimeContext(DNXFile file) {
		registerExternalFunctions(ExternalFunction.getFrom(this));
		this.file = file;
	}
	
	@ExternalDNXFunction("char")
	protected void charImpl(String typer) {
		this.typer = typer;
	}
	
	public void setMissingExternalFunctionHandler(BiFunction<String, Object[], ?> handler) {
		if(handler == null) {
			missingFunctionHandler = defaultMissingFunctionHandler;
		}
		else {
			missingFunctionHandler = handler;
		}
	}
	
	public Value callExternal(String name, int argc) {
		ExternalFunction function = externalFunctions.get(name);
		
		Object[] args = new Object[argc];
		Class<?>[] types;
		
		if(function == null) {
			types = new Class<?>[argc];
			Arrays.fill(types, Object.class);
		}
		else {
			 types = function.getArgumentTypes();
		}
		
		for(int i = 0; i < argc; i++) {
			args[i] = stack.pop(types[i]);
		}
		
		if(function == null) {
			return provider.get(missingFunctionHandler.apply(name, args));
		}
		if(function.getArgumentCount() != argc) {
			throw new IllegalArgumentException("Bad argument count for external function " + name + "; expected " + function.getArgumentCount() + ", got " + argc);
		}
		
		return function.execute(args);
	}
	
	public void registerExternalFunctions(Collection<ExternalFunction> functions) {
		for(ExternalFunction function : functions) {
			registerExternalFunction(function);
		}
	}
	public void registerExternalFunctions(ExternalFunction... functions) {
		for(ExternalFunction function : functions) {
			registerExternalFunction(function);
		}
	}
	
	public void registerExternalFunction(ExternalFunction function) {
		if(externalFunctions.containsKey(function.getName())) {
			throw new IllegalArgumentException("Function with name " + function.getName() + " is already defined");
		}
		externalFunctions.put(function.getName(), function);
		function.setValueProvider(provider);
	}
	
	public ExternalFunction getExternalFunction(String name) {
		return externalFunctions.get(name);
	}
	
	public Set<ExternalFunction> getExternalFunctions() {
		return new HashSet<>(externalFunctions.values());
	}
	
	public void populate(int num) {
		if(num > working.length) {
			throw new IllegalArgumentException("expected at most " + working.length + ", got " + num);
		}
		for(int i = num - 1; i >= 0; i--) {
			working[i] = stack.pop();
		}
	}
	
	public void reclaim() {
		for(int i = 0; i < working.length; i++) {
			if(working[i] != null) {
				provider.put(working[i]);
			}
			working[i] = null;
		}
	}
}
