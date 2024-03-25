package net.benjaminurquhart.diannex.decompiler;

import java.util.ArrayList;
import java.util.List;

import net.benjaminurquhart.diannex.DNXBytecode;
import net.benjaminurquhart.diannex.DNXCompiled;
import net.benjaminurquhart.diannex.DNXDisassembler;
import net.benjaminurquhart.diannex.DNXFile;
import net.benjaminurquhart.diannex.runtime.ANSI;
import net.benjaminurquhart.diannex.runtime.Value;
import net.benjaminurquhart.diannex.runtime.ValueStack;
import net.benjaminurquhart.diannex.DNXDisassembler.Block;

import net.benjaminurquhart.diannex.decompiler.statements.*;
import net.benjaminurquhart.diannex.decompiler.statements.compare.*;
import net.benjaminurquhart.diannex.decompiler.statements.operations.*;

// I'm basically just winging this, the only decompiler I have any sort
// of experience with is from UndertaleModTool so the structure is similar.
public class DNXDecompiler {

	public static String decompile(DNXCompiled entry, DNXFile file) {
		DecompileContext context = new DecompileContext(file);
		context.blocks = DNXDisassembler.createBlocks(entry);
		context.workQueue.add(context.blocks.get(0));
		while(!context.workQueue.isEmpty()) {
			decompileBlock(context.workQueue.poll(), context);
		}
		return context.getDecompilationResults();
	}
	
	private static void decompileBlock(Block block, DecompileContext context) {
		if(context.statements.containsKey(block)) {
			return;
		}
		System.out.printf("%s%s%s\n\n", ANSI.GRAY, block.toString(context.file), ANSI.RESET);
		List<Statement> statements = new ArrayList<>();
		context.currentStatements = statements;
		Value[] working = context.working;
		ValueStack stack = context.stack;
		DNXFile file = context.file;
		Statement statement = null;
		int index = 0;
		boolean jumped = false;
		for(DNXBytecode inst : block.contents) {
			if(jumped) {
				throw new IllegalStateException("jump instruction encountered before end of block at index " + index + " of " + block);
			}
			index++;
			statement = null;
			//System.out.printf("%04d / %04d: %s\n", ++index, block.contents.size(), inst.toString(context.file));
			switch(inst.getOpcode()) {
			case ADD:
				context.populate(2);
				statement = new AddStatement(working[0], working[1]);
				break;
			case BITAND:
				context.populate(2);
				statement = new BitAndStatement(working[0], working[1]);
				break;
			case BITLS:
				unimplemented(inst);
				break;
			case BITNEG:
				unimplemented(inst);
				break;
			case BITOR:
				unimplemented(inst);
				break;
			case BITRS:
				unimplemented(inst);
				break;
			case BITXOR:
				unimplemented(inst);
				break;
			case CALL:
			case CALLEXT:
				Value[] args = new Value[inst.getSecondArg()];
				for(int i = 0; i < args.length; i++) {
					args[i] = stack.pop();
					if(args[i].get() instanceof Statement s) {
						statements.remove(s);
					}
				}
				statement = new FunctionCallStatement(inst.parseFirst(file), args);
				break;
			case CHOICEADD:
				unimplemented(inst);
				break;
			case CHOICEADDT:
				unimplemented(inst);
				break;
			case CHOICEBEG:
				unimplemented(inst);
				break;
			case CHOICESEL:
				unimplemented(inst);
				break;
			case CHOOSEADD:
				unimplemented(inst);
				break;
			case CHOOSEADDT:
				unimplemented(inst);
				break;
			case CHOOSESEL:
				unimplemented(inst);
				break;
			case CMPEQ:
				context.populate(2);
				statement = new EqualStatement(working[0], working[1]);
				break;
			case CMPGT:
				context.populate(2);
				statement = new GreaterThanStatement(working[0], working[1]);
				break;
			case CMPGTE:
				context.populate(2);
				statement = new GreaterThanEqualStatement(working[0], working[1]);
				break;
			case CMPLT:
				context.populate(2);
				statement = new LessThanStatement(working[0], working[1]);
				break;
			case CMPLTE:
				context.populate(2);
				statement = new LessThanEqualStatement(working[0], working[1]);
				break;
			case CMPNEQ:
				context.populate(2);
				statement = new NotEqualStatement(working[0], working[1]);
				break;
			case DIV:
				context.populate(2);
				statement = new DivideStatement(working[0], working[1]);
				break;
			case DUP:
				Object top = stack.peek().get();
				if(top instanceof Statement s) {
					statements.add(s);
				}
				stack.pushObj(top);
				break;
			case DUP2:
				unimplemented(inst);
				break;
			case EXIT:
				statement = new ExitStatement();
				jumped = true;
				break;
			case FREELOC:
				//unimplemented(inst);
				break;
			case INV:
				context.populate(1);
				statement = new InvertStatement(working[0]);
				break;
			case J:
			case JT:
				jumped = true;
				break;
			case JF:
				context.populate(1);
				statement = new IfStatement(working[0]);
				jumped = true;
				break;
			case LOAD:
				unimplemented(inst);
				break;
			case MAKEARR:
				unimplemented(inst);
				break;
			case MOD:
				unimplemented(inst);
				break;
			case MUL:
				unimplemented(inst);
				break;
			case NEG:
				unimplemented(inst);
				break;
			case NOP:
				break;
			case PATCH_CALL: // Should never happen
				unimplemented(inst);
				break;
			case POP:
				stack.pop();
				//unimplemented(inst);
				break;
			case POW:
				unimplemented(inst);
				break;
			case PUSHARRIND:
				unimplemented(inst);
				break;
			case PUSHBINTS:
				unimplemented(inst);
				break;
			case PUSHBS:
			case PUSHS:
				stack.pushObj(inst.parseFirst(file));
				//unimplemented(inst);
				break;
			case PUSHD:
				stack.pushObj(inst.getDoubleArg());
				break;
			case PUSHI:
				stack.pushObj(inst.getFirstArg());
				break;
			case PUSHINTS:
				unimplemented(inst);
				break;
			case PUSHU:
				stack.pushObj(Value.NULL);
				break;
			case PUSHVARGLB:
				statement = new VarGetStatement(inst.parseFirst(file));
				break;
			case PUSHVARLOC:
				if(context.numLocals < inst.getFirstArg() + 1) {
					context.numLocals = inst.getFirstArg() + 1;
				}
				statement = new VarGetStatement("$loc" + inst.getFirstArg());
				break;
			case RET:
				unimplemented(inst);
				break;
			case SAVE:
				unimplemented(inst);
				break;
			case SETARRIND:
				unimplemented(inst);
				break;
			case SETVARGLB:
				context.populate(1);
				statement = new VarSetStatement(inst.parseFirst(file), working[0]);
				break;
			case SETVARLOC:
				context.populate(1);
				if(context.numLocals < inst.getFirstArg() + 1) {
					context.numLocals = inst.getFirstArg() + 1;
				}
				statement = new VarSetStatement("$loc" + inst.getFirstArg(), working[0]);
				break;
			case SUB:
				context.populate(2);
				statement = new SubtractStatement(working[0], working[1]);
				break;
			case TEXTRUN:
				statement = new TextrunStatement(stack.pop().get(String.class));
				break;
			default:
				unimplemented(inst);
			}
			if(statement != null) {
				statements.add(statement);
				stack.pushObj(statement);
			}
		}
		context.statements.put(block, statements);
		if(block.conditionalExit && block.right != null) {
			context.workQueue.add(block.right);
		}
		if(block.left != null) {
			context.workQueue.add(block.left);
		}
	}
	
	private static void unimplemented(DNXBytecode inst) {
		throw new UnsupportedOperationException("no handling for " + inst);
	}
}
