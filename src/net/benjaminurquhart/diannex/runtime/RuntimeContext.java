package net.benjaminurquhart.diannex.runtime;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
		//waitForInput();
	};
	
	public void waitForInput() {
		waitingForInput = true;
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
		waitingForInput = false;
	}
	
	private BiFunction<String, Object[], ?> missingFunctionHandler = defaultMissingFunctionHandler;
	private Map<String, ExternalFunction> externalFunctions = new HashMap<>();
	
	protected BiConsumer<RuntimeContext, String> textrunHandler = defaultTextrunHandler;
	
	private boolean autodefineGlobals, headless, verbose;
	private String typer = "Narrator";
	
	public Map<Integer, Value> localVars = Collections.synchronizedMap(new HashMap<>());
	public Map<String, Value> globalVars = Collections.synchronizedMap(new HashMap<>());
	public ValueProvider provider = new ValueProvider();
	public Value saveRegister;
	
	public final DNXRuntime runtime;
	
	public RuntimeContext parent;
	
	public ValueStack stack = new ValueStack(provider);
	public Value[] working = new Value[3];
	public Map<Integer, Value> flags;
	public DNXFile file;
	
	protected List<Choice> choices;
	protected Choicer choicer;
	
	private volatile boolean waitingForInput, didNewline;
	
	public boolean choiceBeg, didTextRun, choiced, enforcePointerBounds, isParallelScene;
	public int ptr, depth;
	
	public RuntimeContext(DNXRuntime runtime, DNXFile file) {
		registerExternalFunctions(ExternalFunction.getFrom(this));
		this.runtime = runtime;
		this.file = file;
	}

	@ExternalDNXFunction("char")
	protected void charImpl(String typer) {
		this.typer = typer;
	}
	
	public void enforcePointerBounds(boolean enforce) {
		enforcePointerBounds = enforce;
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
		
		Class<?>[] types;
		
		if(function == null) {
			types = new Class<?>[argc];
			Arrays.fill(types, Object.class);
		}
		else {
			 types = function.getArgumentTypes();
		}
		
		Object[] args = new Object[Math.max(argc, types.length)];
		
		for(int i = 0; i < argc; i++) {
			args[i] = stack.pop(types[i]);
		}
		for(int i = 0; i < args.length; i++) {
			if(args[i] == null) {
				args[i] = Value.NULL.get(types[i]);
			}
		}
		
		if(function == null) {
			return provider.get(missingFunctionHandler.apply(name, args));
		}
		if(function.isVarArg() && function.getArgumentCount() < argc) {
			throw new IllegalArgumentException("Bad argument count for external function " + name + "; expected at most " + function.getArgumentCount() + ", got " + argc);
		}
		else if(!function.isVarArg() && function.getArgumentCount() != argc) {
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
	
	public BiFunction<String, Object[], ?> getMissingExternalFunctionHandler() {
		return missingFunctionHandler;
	}
	
	public ExternalFunction getExternalFunction(String name) {
		return externalFunctions.get(name);
	}
	
	public Set<ExternalFunction> getExternalFunctions() {
		return new HashSet<>(externalFunctions.values());
	}
	
	public void autodefineGlobals(boolean state) {
		autodefineGlobals = state;
	}
	
	public boolean autodefineGlobals() {
		return autodefineGlobals;
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
	
	public Choicer getChoicer() {
		return choicer;
	}
	
	public void insertChoice(String name, double percentage, int jump) {
		if(!choiceBeg) {
			throw new IllegalStateException("Cannot insert choice outside of choice mode");
		}
		choicer.addChoice(name, percentage, ptr + jump);
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
				System.out.printf("%s%sAutodefining globalvar %s as 0%s\n", ANSI.GRAY, parallelString(), name, ANSI.RESET);
				setGlobal(name, 0);
			}
			else {
				throw new IllegalStateException("Unknown global var: " + name);
			}
		}
		Value out = globalVars.get(name);
		System.out.printf("%s%sGet globalvar %s -> %s%s\n", ANSI.GRAY, parallelString(), name, out.get(), ANSI.RESET);
		return out;
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
		if(localVars.containsKey(index) && flags != null && index < flags.size()) {
			flags.put(index, localVars.get(index));
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
		flags = null;
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
	
	public String parallelString() {
		if(isParallelScene) {
			if(!didNewline && parent.waitingForInput) {
				didNewline = true;
				return "\n[PARALLEL SCENE]: ";
			}
			return "[PARALLEL SCENE]: ";
		}
		didNewline = false;
		return "";
	}
	
	@Override
	public String toString() {
		return String.format("RuntimeContext %08x [%d global vars, %d local vars, %d external functions]", hashCode(), globalVars.size(), localVars.size(), externalFunctions.size());
	}
}
