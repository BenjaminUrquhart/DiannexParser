package net.benjaminurquhart.diannex.decompiler;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;


import net.benjaminurquhart.diannex.DNXFile;
import net.benjaminurquhart.diannex.decompiler.statements.Statement;
import net.benjaminurquhart.diannex.DNXDisassembler.Block;
import net.benjaminurquhart.diannex.runtime.Value;
import net.benjaminurquhart.diannex.runtime.ValueStack;

public class DecompileContext {
	
	public DNXFile file;
	public Set<String> vars;
	public ValueStack stack;
	
	public Value[] working;
	
	public int numLocals;
	public List<Statement> currentStatements;
	
	public Map<Block, List<Statement>> statements;
	public Map<Integer, Block> blocks;
	
	public Queue<Block> workQueue;
	
	protected DecompileContext(DNXFile file) {
		this.working = new Value[2];
		this.workQueue = new ArrayDeque<>();
		this.statements = new HashMap<>();
		this.blocks = new HashMap<>();
		this.stack = new ValueStack();
		this.file = file;
	}
	
	public void populate(int num) {
		if(num > working.length) {
			throw new IllegalArgumentException("expected at most " + working.length + ", got " + num);
		}
		for(int i = num - 1; i >= 0; i--) {
			working[i] = stack.pop();
			if(currentStatements != null && (working[i].get() instanceof Statement s)) {
				currentStatements.remove(s);
			}
		}
	}
	
	public String getDecompilationResults() {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < numLocals; i++) {
			sb.append("local $loc");
			sb.append(i);
			sb.append(";\n");
		}
		sb.append(stringifyBlock(blocks.get(0), 0, new HashSet<>()));
		return sb.toString();
	}
	
	private String stringifyBlock(Block block, int indentation, Set<Block> reserved) {
		StringBuilder sb = new StringBuilder();
		String indent = "\t".repeat(indentation);
		for(Statement s : statements.get(block)) {
			sb.append(s.toString(indentation));
			sb.append("\n");
		}
		// hack
		boolean hasLeft = block.left != null & reserved.add(block.left);
		boolean hasRight = block.right != null && reserved.add(block.right);
		if(hasLeft && block.conditionalExit) {
			sb.append(indent);
			sb.append("{\n");
			sb.append(stringifyBlock(block.left, indentation + 1, reserved));
			sb.append(indent);
			sb.append("}\n");
		}
		if(hasRight) {
			sb.append(stringifyBlock(block.right, indentation, reserved));
		}
		return sb.toString();
	}
}
