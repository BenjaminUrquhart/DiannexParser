package net.benjaminurquhart.diannex;

import java.nio.ByteBuffer;
import java.util.List;

public class DNXFunction extends DNXCompiled {
	
	public DNXFunction(ByteBuffer reader) {
		symbolPointer = reader.getInt();
		int size = reader.getShort();
		for(int i = 0; i < size; i++) {
			bytecodeIndicies.add(reader.getInt());
		}
	}
	
	public DNXFunction(DNXString name, List<DNXBytecode> instructions) {
		this.instructions = instructions;
		this.name = name;
	}
}
