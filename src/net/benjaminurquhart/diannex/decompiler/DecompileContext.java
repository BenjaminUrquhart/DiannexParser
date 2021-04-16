package net.benjaminurquhart.diannex.decompiler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


import net.benjaminurquhart.diannex.DNXFile;
import net.benjaminurquhart.diannex.DNXDisassembler.Block;
import net.benjaminurquhart.diannex.runtime.ValueStack;

public class DecompileContext {
	
	public DNXFile file;
	public Set<String> vars;
	public ValueStack stack;
	
	public Map<Block, List<String>> statements;
	
	protected DecompileContext(DNXFile file) {
		this.statements = new HashMap<>();
		this.stack = new ValueStack();
		this.file = file;
	}
}
