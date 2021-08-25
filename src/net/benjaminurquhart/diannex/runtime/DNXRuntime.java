package net.benjaminurquhart.diannex.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import net.benjaminurquhart.diannex.DNXBytecode;
import net.benjaminurquhart.diannex.DNXFile;
import net.benjaminurquhart.diannex.DNXBytecode.Opcode;
import net.benjaminurquhart.diannex.DNXCompiled;

public class DNXRuntime {
	
	private static final Set<Opcode> BANNED_IN_CHOICE = EnumSet.of(
			Opcode.CHOICEBEG,
			Opcode.CHOOSEADD,
			Opcode.CHOOSEADDT,
			Opcode.CHOOSESEL
	);
	
	public static Value simpleEval(DNXCompiled entry, DNXFile file) {
		try {
			return new DNXRuntime(file).eval(entry).get();
		}
		catch(RuntimeException e) {
			throw e;
		}
		catch(Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
	public static Value simpleEval(List<DNXBytecode> instructions, DNXFile file) {
		try {
			return new DNXRuntime(file).eval(instructions).get();
		}
		catch(RuntimeException e) {
			throw e;
		}
		catch(Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
	
	private RuntimeContext context;
	private volatile boolean suspended;
	
	public DNXRuntime(DNXFile file) {
		this.context = new RuntimeContext(this, file);
	}
	
	public RuntimeContext getContext() {
		return context;
	}
	
	public boolean isSuspended() {
		return suspended;
	}
	
	public boolean setSuspended(boolean suspended) {
		return this.suspended = suspended;
	}
	
	public CompletableFuture<Value> eval(DNXCompiled entry) {
		return  CompletableFuture.supplyAsync(() -> internalEval(entry));
	}
	
	public CompletableFuture<Value> eval(List<DNXBytecode> instructions) {
		if(context.runtime != this) {
			throw new IllegalStateException("Current context is not bound to this runtime");
		}
		return CompletableFuture.supplyAsync(() -> internalEval(instructions));
	}
	
	private Value internalEval(DNXCompiled entry) {
		int offset = context.localVars.size();
		for(int i = 0; i < entry.flags.size(); i++) {
			context.setLocal(i + offset, internalEval(entry.flags.get(i).valueBytecode));
		}
		if(!context.localVars.isEmpty()) {
			System.out.printf("%sLocals: %s%s\n", ANSI.GRAY, context.localVars, ANSI.RESET);
		}
		return internalEval(entry.instructions);
	}
	
	private Value internalEval(List<DNXBytecode> instructions) {
		DNXBytecode[] insts = instructions.toArray(DNXBytecode[]::new);
		DNXBytecode inst;
		
		String stackStr = null, instStr;
		
		List<String> prevInstructions = new ArrayList<>();
		
		for(int ptr = 0; ptr < insts.length; ptr++) {
			
			while(suspended);
			
			context.ptr = ptr;
			inst = insts[ptr];
			
			instStr = ">".repeat(context.depth) + " " + ptr + ": " + inst.toString(context.file);
			
			prevInstructions.add(instStr);
			while(prevInstructions.size() > 10) {
				prevInstructions.remove(0);
			}
			
			if(inst.getOpcode() == Opcode.RET || inst.getOpcode() == Opcode.EXIT) {
				break;
			}
			stackStr = stackToStr(context.stack);
			
			if(context.isVerbose()) {
				System.out.println(instStr + " " + stackStr);
			}
			
			try {
				if(evalSingle(inst)) {
					ptr += inst.getFirstArg() - 1;
				}
				else if(context.choiced) {
					context.clearChoiceState();
					ptr = context.ptr - 1;
				}
				
				if(ptr < -1 || ptr >= insts.length - 1) {
					throw new IllegalStateException("Pointer moved out of bounds: " + ptr);
				}
			}
			catch(Throwable e) {
				System.out.flush();
				System.err.flush();
				System.err.println("Execution error at instruction " + ptr + ": " + inst.toString(context.file));
				if(prevInstructions.size() > 1) {
					System.err.println("Last " + prevInstructions.size() + " instructions:");
					for(String prev : prevInstructions) {
						System.err.println(prev);
					}
				}
				System.err.println("Stack: " + stackStr);
				
				if(e instanceof RuntimeException) {
					throw (RuntimeException)e;
				}
				throw new RuntimeException(e);
			}
			//System.out.println(context.stack);
		}
		return context.stack.isEmpty() ? null : context.stack.peek();
	}
	
	private boolean evalSingle(DNXBytecode inst) {
		if(context.choiceBeg) {
			if(BANNED_IN_CHOICE.contains(inst.getOpcode())) {
				throw new IllegalStateException("Cannot execute " + inst.getOpcode() + " in choice mode");
			}
			if(inst.getOpcode() == Opcode.TEXTRUN && context.didTextRun) {
				throw new IllegalStateException("Cannot execute textrun more than once in choice mode");
			}
		}
		
		int ptr = context.ptr;
		
		ValueStack stack = context.stack;
		Value[] working = context.working;
		
		context.reclaim();
		
		switch(inst.getOpcode()) {
		case PUSHU:
			stack.pushObj(null);
			break;
		case PUSHI:
			stack.pushObj(inst.getFirstArg());
			break;
		case PUSHD:
			stack.pushObj(inst.getDoubleArg());
			break;
		case PUSHS:
		case PUSHBS:
			stack.pushObj(inst.parseFirst(context.file, false));
			break;
		case PUSHINTS:
		case PUSHBINTS:
			stack.pushObj(fillInterpolatedString(inst.parseFirst(context.file, false), inst.getSecondArg()));
			break;
		
		case MAKEARR:
			int size = inst.getFirstArg();
			Object[] arr = new Object[size];
			for(int i = size - 1; i >= 0; i--) {
				arr[i] = stack.pop(Object.class);
			}
			stack.pushObj(arr);
			break;
		case PUSHARRIND:
			context.populate(2);
			stack.pushObj(working[0].get(Object[].class)[working[1].get(int.class)]);
			break;
		case SETARRIND:
			context.populate(3);
			working[0].get(Object[].class)[working[1].get(int.class)] = working[2].get();
			stack.pushObj(working[0]);
			break;
		
		case SETVARGLB:
			context.setGlobal(inst.parseFirst(context.file, false), stack.pop());
			break;
		case SETVARLOC:
			context.setLocal(inst.getFirstArg(), stack.pop());
			break;
		case PUSHVARLOC:
			stack.pushObj(context.getLocal(inst.getFirstArg()));
			break;
		case PUSHVARGLB:
			stack.pushObj(context.getGlobal(inst.parseFirst(context.file, false)));
			break;
			
		case CHOICEBEG:
			context.choiceBeg = true;
			context.didTextRun = false;
			context.choicer = new Choicer();
			break;
			
		case CHOICEADD:
			context.populate(2);
			context.choicer.addChoice(working[0].get(String.class), working[1].get(double.class), ptr + inst.getFirstArg());
			break;
		case CHOICEADDT:
			context.populate(3);
			if(working[2].get(boolean.class)) {
				context.choicer.addChoice(working[1].get(String.class), working[0].get(double.class), ptr + inst.getFirstArg());
			}
			break;
		case CHOICESEL:
			context.ptr = context.isHeadless() ? context.choicer.processChoices().get(0).jump : context.choicer.getChoice().jump;
			context.choiced = true;
			break;
			
		case CHOOSEADD:
			if(context.choices == null) {
				context.choices = new ArrayList<>();
			}
			context.choices.add(new Choice("-", stack.pop(double.class), ptr + inst.getFirstArg()));
			break;
		case CHOOSEADDT:
			context.populate(2);
			if(working[1].get(boolean.class)) {
				context.choices.add(new Choice("-", working[0].get(double.class), ptr + inst.getFirstArg()));
			}
			break;
		case CHOOSESEL:
			double totalWeight = 0, rand = Math.random(), prev = 0;
			for(Choice choice : context.choices) {
				totalWeight += choice.chance;
			}
			for(Choice choice : context.choices) {
				choice.chance /= totalWeight;
			}
			Collections.shuffle(context.choices);
			for(Choice choice : context.choices) {
				if(rand > prev && rand <= prev + choice.chance) {
					context.ptr = choice.jump;
					context.choiced = true;
					return false;
				}
				prev += choice.chance;
			}
			throw new IllegalStateException("Failed to choicesel! No option was selected.");
		case POP:
			stack.pop();
			break;
		case DUP2:
			stack.push(stack.peek());
		case DUP:
			stack.push(stack.peek());
			break;
		
		case ADD:
			context.populate(2);
			if((working[0].get() instanceof String) || (working[1].get() instanceof String)) {
				stack.pushObj(working[0].get(String.class) + working[1].get(String.class));
			}
			else {
				stack.pushObj(working[0].get(double.class) + working[1].get(double.class));
			}
			break;
		case SUB:
			context.populate(2);
			stack.pushObj(working[0].get(double.class) - working[1].get(double.class));
			break;
		case MUL:
			context.populate(2);
			stack.pushObj(working[0].get(double.class) * working[1].get(double.class));
			break;
		case DIV:
			context.populate(2);
			stack.pushObj(working[0].get(double.class) / working[1].get(double.class));
			break;
		case MOD:
			context.populate(2);
			stack.pushObj(working[0].get(double.class) % working[1].get(double.class));
			break;
		case NEG:
			context.populate(1);
			stack.pushObj(-working[0].get(double.class));
			break;
		case INV:
			context.populate(1);
			stack.pushObj(working[0].get(int.class) == 0 ? 1 : 0);
			break;
			
		case BITLS:
			context.populate(2);
			stack.pushObj(working[0].get(int.class) << working[1].get(int.class));
			break;
		case BITRS:
			context.populate(2);
			stack.pushObj(working[0].get(int.class) >> working[1].get(int.class));
			break;
		case BITAND:
			context.populate(2);
			stack.pushObj(working[0].get(int.class) & working[1].get(int.class));
			break;
		case BITOR:
			context.populate(2);
			stack.pushObj(working[0].get(int.class) | working[1].get(int.class));
			break;
		case BITXOR:
			context.populate(2);
			stack.pushObj(working[0].get(int.class) ^ working[1].get(int.class));
			break;
		case BITNEG:
			context.populate(1);
			stack.pushObj(~working[0].get(int.class));
			break;
			
		case POW:
			context.populate(2);
			stack.pushObj(Math.pow(working[0].get(double.class), working[1].get(double.class)));
			break;
		
		case CMPEQ:
			context.populate(2);
			stack.pushObj(working[0].get(Object.class).equals(working[1].get(Object.class)));
			break;
		case CMPGT:
			context.populate(2);
			stack.pushObj(working[0].get(double.class) > working[1].get(double.class));
			break;
		case CMPLT:
			context.populate(2);
			stack.pushObj(working[0].get(double.class) < working[1].get(double.class));
			break;
		case CMPGTE:
			context.populate(2);
			stack.pushObj(working[0].get(double.class) >= working[1].get(double.class));
			break;
		case CMPLTE:
			context.populate(2);
			stack.pushObj(working[0].get(double.class) <= working[1].get(double.class));
			break;
		case CMPNEQ:
			context.populate(2);
			stack.pushObj(!working[0].get(Object.class).equals(working[1].get(Object.class)));
			break;
			
		case J:
			return true;
		case JF:
			return !stack.pop().get(boolean.class);
		case JT:
			return stack.pop().get(boolean.class);
			
		case EXIT:
		case RET:
			break;
		
		case TEXTRUN:
			context.textrunHandler.accept(context, stack.pop(String.class));
			if(context.choiceBeg) {
				context.didTextRun = true;
			}
			break;
			
		case CALL:
			Map<Integer, Value> oldLocalVars = context.localVars;
			context.localVars = new HashMap<>();
			context.stack = new ValueStack();
			
			for(int i = 0; i < inst.getSecondArg(); i++) {
				context.setLocal(i, stack.pop());
			}
			
			context.depth++;
			stack.push(internalEval(context.file.functionByName(inst.parseFirst(context.file, false))));
			context.localVars = oldLocalVars;
			context.stack = stack;
			context.ptr = ptr;
			context.depth--;
			break;
		case CALLEXT:
			context.depth++;
			stack.push(context.callExternal(inst.parseFirst(context.file, false), inst.getSecondArg()));
			context.ptr = ptr;
			context.depth--;
			break;
			
		case FREELOC:
			context.freeLocal(inst.getFirstArg());
			break;
			
		case SAVE:
			context.saveRegister = stack.peek();
			break;
		case LOAD:
			if(context.saveRegister == null) {
				throw new IllegalStateException("Cannot load a value without saving it");
			}
			stack.push(context.saveRegister);
			context.saveRegister = null;
			break;
			
		default:
			throw new UnsupportedOperationException("Unimplemented opcode: " + inst.getOpcode());
		}

		
		return false;
	}
	
	private String stackToStr(ValueStack stack) {
		Object[] objs = new Object[stack.size()];
		for(int i = 0; i < objs.length; i++) {
			objs[i] = stack.pop(Object.class);
		}
		for(int i = objs.length - 1; i >= 0; i --) {
			stack.pushObj(objs[i]);
			if(objs[i] != null && !((objs[i] instanceof Number) || (objs[i].getClass().isArray()))) {
				objs[i] = '"' + objs[i].toString() + '"';
			}
		}
		return Arrays.deepToString(objs);
	}
	
	private String fillInterpolatedString(String str, int argc) {
		for(int i = 0; i < argc; i++) {
			str = str.replace("${" + i + "}", context.stack.pop(String.class));
		}
		return str;
	}
}
