package net.benjaminurquhart.diannex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class DNXCompiled {

	protected DNXString symbol;
	protected int symbolPointer;
	protected List<DNXBytecode> bytecode;
	protected List<Integer> bytecodeIndicies;
	
	private boolean processed;
	
	protected DNXCompiled() {
		this.bytecode = new ArrayList<>();
		this.bytecodeIndicies = new ArrayList<>();
	}

	
	public DNXString getSymbol() {
		return symbol;
	}
	
	public List<DNXBytecode> getBytecode() {
		return Collections.unmodifiableList(bytecode);
	}
	
	protected void postProcess(DNXReader reader) {
		DNXBytecode entry;
		symbol = reader.strings.get(symbolPointer);
		for(int index : bytecodeIndicies) {
			if(index < 0) continue;
			entry = reader.bytecode.get(index);
			reader.entryPoints.add(entry);
			bytecode.add(entry);
		}
		if(!bytecodeIndicies.isEmpty() && bytecodeIndicies.size() % 2 == 0) {
			throw new IllegalStateException(String.format("%s %s has unpaired flags", this.getClass().getSimpleName(), symbol.getClean()));
		}
		processed = true;
	}
	
	public String disassemble(DNXReader reader) {
		if(bytecode.isEmpty()) {
			return symbol.get() + " has no bytecode entries.";
		}
		
		List<String> asm = DNXDisassembler.disassemble(bytecode.get(0), reader);
		
		StringBuilder sb = new StringBuilder(symbol.get());
		int size = bytecode.size();
		if(size > 1) {
			sb.append(" (flags=[");
			String flag, value;
			for(int i = 1; i < size; i += 2) {
				flag = reader.strings.get(bytecode.get(i + 1).getFirstArg()).getClean();
				value = String.valueOf(bytecode.get(i).getFirstArg());
				
				sb.append(flag);
				sb.append("=");
				sb.append(value);
				
				if(i + 1 < size - 1) {
					sb.append(", ");
				}
			}
			sb.append("])");
		}
		sb.append(":");
		
		for(String s : asm) {
			sb.append("\n");
			sb.append(s);
		}
		
		return sb.toString();
	}
	
	public String toString() {
		int size = processed ? bytecodeIndicies.size() : bytecode.size();
		return String.format("%s %s [%d bytecode %s]", this.getClass().getSimpleName(), symbol.getClean(), size, size == 1 ? "entry" : "entries");
	}
}
