package net.benjaminurquhart.diannex.runtime;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
		return new DNXRuntime(file).eval(entry);
	}
	
	public static Value simpleEval(List<DNXBytecode> instructions, DNXFile file) {
		return new DNXRuntime(file).eval(instructions);
	}
	
	
	private RuntimeContext context;
	
	public DNXRuntime(DNXFile file) {
		this.context = new RuntimeContext(file);
	}
	
	public RuntimeContext getContext() {
		return context;
	}
	
	public Value eval(DNXCompiled entry) {
		for(int i = 0; i < entry.flags.size(); i++) {
			context.localVars.put(i, eval(entry.flags.get(i).keyBytecode));
			System.out.printf("%s%s (%s) -> %s%s\n", ANSI.GRAY, eval(entry.flags.get(i).valueBytecode), i, context.localVars.get(i), ANSI.RESET);
		}
		return eval(entry.instructions);
	}
	
	public Value eval(List<DNXBytecode> instructions) {
		Map<Integer, Value> oldLocalVars = context.localVars;
		context.localVars = new HashMap<>();
		
		DNXBytecode[] insts = instructions.toArray(DNXBytecode[]::new);
		DNXBytecode inst;
		
		String stackStr = null;
		
		for(int ptr = 0; ptr < insts.length; ptr++) {
			context.ptr = ptr;
			inst = insts[ptr];
			
			//System.out.println(inst.toString(context.file));
			
			if(inst.getOpcode() == Opcode.RET || inst.getOpcode() == Opcode.EXIT) {
				break;
			}
			stackStr = context.stack.toString();
			try {
				if(evalSingle(inst)) {
					ptr += inst.getFirstArg() - 1;
				}
				else if(context.choiced) {
					context.didTextRun = false;
					context.choiceBeg = false;
					context.choiced = false;
					context.choicer = null;
					ptr = context.ptr - 1;
				}
			}
			catch(Throwable e) {
				System.err.println("Execution error at instruction " + ptr + ": " + inst.toString(context.file));
				System.err.println("Stack: " + stackStr);
				
				if(e instanceof RuntimeException) {
					throw (RuntimeException)e;
				}
				throw new RuntimeException(e);
			}

			
			//System.out.println(context.stack);
		}
		context.localVars = oldLocalVars;
		return context.stack.isEmpty() ? null : context.stack.pop();
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
			stack.pushObj(new Object[stack.pop(int.class)]);
			break;
		case PUSHARRIND:
			context.populate(2);
			stack.pushObj(working[1].get(Object[].class)[working[0].get(int.class)]);
			break;
		case SETARRIND:
			context.populate(3);
			working[1].get(Object[].class)[working[0].get(int.class)] = working[2].get();
			stack.pushObj(working[1]);
			break;
		
		case SETVARGLB:
			context.globalVars.put(inst.parseFirst(context.file, false), stack.pop());
			break;
		case SETVARLOC:
			//System.out.printf("%sset localvar %s = %s%s\n", ANSI.GRAY, inst.getFirstArg(), stack.peek(), ANSI.RESET);
			context.localVars.put(inst.getFirstArg(), stack.pop());
			break;
		case PUSHVARLOC:
			if(!context.localVars.containsKey(inst.getFirstArg())) {
				throw new IllegalStateException("Unknown local var: " + inst.getFirstArg());
			}
			stack.pushObj(context.localVars.get(inst.getFirstArg()));
			//System.out.printf("%sget localvar %s -> %s%s\n", ANSI.GRAY, inst.getFirstArg(), stack.peek(), ANSI.RESET);
			break;
		case PUSHVARGLB:
			String varname = inst.parseFirst(context.file, false);
			if(!context.globalVars.containsKey(varname)) {
				throw new IllegalStateException("Unknown global var: " + varname);
			}
			stack.pushObj(context.globalVars.get(varname));
			break;
			
		case CHOICEBEG:
			context.choiceBeg = true;
			context.didTextRun = false;
			context.choicer = new Choicer();
			break;
			
		case CHOICEADD:
			context.populate(2);
			context.choicer.addChoice(working[0].get(String.class), working[1].get(double.class), context.ptr + inst.getFirstArg());
			break;
		case CHOICEADDT:
			context.populate(3);
			if(working[2].get(boolean.class)) {
				context.choicer.addChoice(working[0].get(String.class), working[1].get(double.class), context.ptr + inst.getFirstArg());
			}
			break;
		case CHOICESEL:
			context.ptr = context.choicer.getChoice().jump;
			context.choiced = true;
			break;
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
			stack.pushObj(working[0].get(int.class) > working[1].get(int.class));
			break;
		case CMPLT:
			context.populate(2);
			stack.pushObj(working[0].get(int.class) < working[1].get(int.class));
			break;
		case CMPGTE:
			context.populate(2);
			stack.pushObj(working[0].get(int.class) >= working[1].get(int.class));
			break;
		case CMPLTE:
			context.populate(2);
			stack.pushObj(working[0].get(int.class) <= working[1].get(int.class));
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
			doTextRun();
			if(context.choiceBeg) {
				context.didTextRun = true;
			}
			break;
			
		case CALL:
			// TODO: limit how far functions can see down the stack so they only see their own arguments
			stack.push(eval(context.file.functionByName(inst.parseFirst(context.file, false)).instructions));
			break;
		case CALLEXT:
			stack.push(context.callExternal(inst.parseFirst(context.file, false), inst.getSecondArg()));
			break;
			
		case FREELOC:
			if(!context.localVars.containsKey(inst.getFirstArg())) {
				throw new IllegalStateException("Unknown local var: " + inst.getFirstArg());
			}
			context.localVars.remove(inst.getFirstArg());
			break;
			
		default:
			throw new UnsupportedOperationException("Unimplemented opcode: " + inst.getOpcode());
		}

		
		return false;
	}
	
	private String fillInterpolatedString(String str, int argc) {
		for(int i = 0; i < argc; i++) {
			str = str.replace("${" + i + "}", context.stack.pop(String.class));
		}
		return str;
	}
	
	private void doTextRun() {
		System.out.printf("[%s] %s", context.typer, context.stack.pop(String.class));
		if(context.choiceBeg) {
			System.out.println();
			return;
		}
		try {
			while(System.in.available() == 0) {
				Thread.sleep(10);
			}
			while(System.in.available() > 0) {
				System.in.read();
			}
		}
		catch(Exception e) {
			System.out.println(e);
		}
	}
}
