package net.benjaminurquhart.diannex;

import java.nio.ByteBuffer;

public class DNXDefinition extends DNXCompiled {

	private int stringReference;
	private DNXString reference;
	
	public DNXDefinition(ByteBuffer reader) {
		symbolPointer = reader.getInt();
		stringReference = reader.getInt();
		bytecodeIndicies.add(reader.getInt());
	}
	
	public void postProcess(DNXReader reader) {
		super.postProcess(reader);
		
		if(stringReference < 0) {
			int newRef = stringReference ^ (1 << 31);
			reference = reader.strings.get(newRef);
		}
		else {
			reference = reader.translations.get(stringReference);
		}
	}
	
	public DNXString getReference() {
		return reference;
	}
	
	public String toString() {
		return String.format("DNXDefinition %s [ref=%s, bytecode=%s]", symbol.get(), reference, bytecode);
	}
}
