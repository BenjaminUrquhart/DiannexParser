package net.benjaminurquhart.diannex;

import java.nio.ByteBuffer;

public class DNXDefinition {

	private int symbolIndex, stringReference, bytecodeIndex;
	
	private DNXString symbol, reference;
	private DNXBytecode bytecode;
	
	public DNXDefinition(ByteBuffer reader) {
		symbolIndex = reader.getInt();
		stringReference = reader.getInt();
		bytecodeIndex = reader.getInt();
		
		//System.out.printf("DNXDefinition [symbol=%d, ref=%d, bytecode=%d]\n", symbolIndex, stringReference, bytecodeIndex);
	}
	
	public void postProcess(DNXReader reader) {
		symbol = reader.strings.get(symbolIndex);
		bytecode = bytecodeIndex < 0 ? null : reader.bytecode.get(bytecodeIndex);
		
		if(stringReference < 0) {
			int newRef = stringReference ^ (1 << 31);
			//System.out.println(stringReference + " -> " + newRef);
			reference = reader.strings.get(newRef);
		}
		else {
			reference = reader.translations.get(stringReference);
		}
		
		//System.out.println(this);
	}
	
	public DNXString getSymbol() {
		return symbol;
	}
	
	public DNXString getReference() {
		return reference;
	}
	
	public DNXBytecode getBytecode() {
		return bytecode;
	}
	
	public String toString() {
		return String.format("DNXDefinition %s [ref=%s, bytecode=%s]", symbol.get(), reference, bytecode);
	}
}
