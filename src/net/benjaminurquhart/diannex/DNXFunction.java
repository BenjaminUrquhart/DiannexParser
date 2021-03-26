package net.benjaminurquhart.diannex;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DNXFunction {
	
	private int symbolPointer;
	private List<Integer> bytecodeIndicies;
	
	private DNXString symbol;
	private List<DNXBytecode> bytecode;
	
	public DNXFunction(ByteBuffer reader) {
		symbolPointer = reader.getInt();
		
		bytecodeIndicies = new ArrayList<>();
		int size = reader.getShort();
		for(int i = 0; i < size; i++) {
			bytecodeIndicies.add(reader.getInt());
		}
		
		//System.out.printf("DNXFunction [symbol=%d, indicies=%s]\n", symbolPointer, bytecodeIndicies);
	}
	
	protected void postProcess(DNXReader reader) {
		DNXBytecode entry;
		symbol = reader.strings.get(symbolPointer);
		bytecode = new ArrayList<>();
		for(int index : bytecodeIndicies) {
			entry = reader.bytecode.get(index);
			reader.entryPoints.add(entry);
			bytecode.add(entry);
		}
		//System.out.println(this);
	}
	
	public DNXString getSymbol() {
		return symbol;
	}
	
	public List<DNXBytecode> getBytecode() {
		return Collections.unmodifiableList(bytecode);
	}
	
	public String toString() {
		return String.format("DNXFunction %s [%d script(s)]", symbol, bytecode.size());
	}
}
