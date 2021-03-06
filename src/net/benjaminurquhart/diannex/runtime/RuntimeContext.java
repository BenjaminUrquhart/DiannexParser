package net.benjaminurquhart.diannex.runtime;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import net.benjaminurquhart.diannex.DNXFile;

public class RuntimeContext {
	
	private static final BiFunction<String, Object[], ?> defaultMissingFunctionHandler = (name, args) -> {
		throw new IllegalArgumentException("Undefined external function: " + name);
	};
	
	private static final BiConsumer<RuntimeContext, String> defaultTextrunHandler = (context, text) -> {
		System.out.printf("[%s] %s", context.typer, text);
		if(context.choiceBeg) {
			System.out.println();
			return;
		}
		waitForInput();
	};
	
	public static void waitForInput() {
		try {
			while(System.in.available() == 0) {
				Thread.sleep(10);
			}
			while(System.in.available() > 0) {
				System.in.read();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private BiFunction<String, Object[], ?> missingFunctionHandler = defaultMissingFunctionHandler;
	private Map<String, ExternalFunction> externalFunctions = new HashMap<>();
	
	protected BiConsumer<RuntimeContext, String> textrunHandler = defaultTextrunHandler;
	
	private boolean autodefineGlobals, headless, verbose;
	private String typer = "Narrator";
	
	public Map<Integer, Value> localVars = new HashMap<>();
	public Map<String, Value> globalVars = new HashMap<>();
	public ValueProvider provider = new ValueProvider();
	public Value saveRegister;
	
	public final DNXRuntime runtime;
	
	public ValueStack stack = new ValueStack(provider);
	public Value[] working = new Value[3];
	public DNXFile file;
	
	protected List<Choice> choices;
	protected Choicer choicer;
	
	protected boolean choiceBeg, didTextRun, choiced;
	protected int ptr, depth;
	
	public RuntimeContext(DNXRuntime runtime, DNXFile file) {
		registerExternalFunctions(ExternalFunction.getFrom(this));
		this.runtime = runtime;
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
		
		if(function.takesContext()) {
			Object[] newArgs = new Object[argc + 1];
			newArgs[0] = this;
			System.arraycopy(args, 0, newArgs, 1, args.length);
			args = newArgs;
		}
		try {
			return function.execute(args);
		}
		catch(RuntimeException e) {
			System.err.println("Exception while executing external function");
			System.err.printf("Call: %s(%s)\n", name, Arrays.deepToString(args));
			throw e;
		}
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
	
	public boolean autodefineGlobals(boolean state) {
		return autodefineGlobals = state;
	}
	
	public void makeHeadless() {
		this.headless = true;
		this.autodefineGlobals = true;
		this.textrunHandler = (ctx, text) -> {};
		this.missingFunctionHandler = (name, args) -> 0;
	}
	
	public boolean isHeadless() {
		return headless;
	}
	
	public boolean setVerbose(boolean verbose) {
		return this.verbose = verbose;
	}
	
	public boolean isVerbose() {
		return verbose;
	}
	
	public boolean isChoicing() {
		return choiceBeg;
	}
	
	public void setTextrunHandler(BiConsumer<RuntimeContext, String> handler) {
		if(handler == null) {
			textrunHandler = defaultTextrunHandler;
		}
		else {
			textrunHandler = handler;
		}
	}
	public Value getGlobal(String name) {
		if(!globalVars.containsKey(name)) {
			if(autodefineGlobals) {
				System.out.printf("%sAutodefining globalvar %s as 0%s\n", ANSI.GRAY, name, ANSI.RESET);
				setGlobal(name, 0);
			}
			else {
				throw new IllegalStateException("Unknown global var: " + name);
			}
		}
		return globalVars.get(name);
	}
	
	public <T> T getGlobal(String name, Class<T> type) {
		return getGlobal(name).get(type);
	}
	
	public void setGlobal(String name, Object value) {
		setVar(globalVars, name, value);
	}
	
	public Value getLocal(int index) {
		if(!localVars.containsKey(index)) {
			throw new IllegalStateException("Unknown local var: " + index);
		}
		return localVars.get(index);
	}
	
	public <T> T getLocal(int index, Class<T> type) {
		return getLocal(index).get(type);
	}
	
	public void setLocal(int index, Object value) {
		setVar(localVars, index, value);
	}
	
	public void freeLocal(int index) {
		if(!localVars.containsKey(index)) {
			throw new IllegalStateException("Unknown local var: " + index);
		}
		localVars.remove(index);
	}
	
	public Set<String> getGlobals() {
		return new HashSet<>(globalVars.keySet());
	}
	
	public Set<Integer> getLocals() {
		return new HashSet<>(localVars.keySet());
	}
	
	private <T> void setVar(Map<T, Value> map, T key, Object value) {
		if(map.containsKey(key)) {
			if(value instanceof Value) {
				value = ((Value)value).get();
			}
			map.get(key).update(value);
		}
		else {
			map.put(key, provider.get(value));
		}
	}
	
	protected void setTyper(String typer) {
		this.typer = typer;
	}
	
	public String getTyper() {
		return typer;
	}
	
	public DNXRuntime getRuntime() {
		return runtime;
	}
	
	public void clearChoiceState() {
		if(choices != null) {
			choices.clear();
		}
		didTextRun = false;
		choiceBeg = false;
		choiced = false;
		choicer = null;
	}
	
	public void reset() {
		if(choices != null) {
			choices.clear();
		}
		saveRegister = null;
		typer = "Narrator";
		localVars.clear();
		stack.clear();
		depth = 0;
		ptr = 0;
		
		this.clearChoiceState();
		this.reclaim();
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
	
	@Override
	public String toString() {
		return String.format("RuntimeContext %08x [%d global vars, %d local vars, %d external functions]", hashCode(), globalVars.size(), localVars.size(), externalFunctions.size());
	}
}
