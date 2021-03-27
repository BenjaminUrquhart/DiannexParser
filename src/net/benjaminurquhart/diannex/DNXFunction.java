package net.benjaminurquhart.diannex;

import java.nio.ByteBuffer;

public class DNXFunction extends DNXCompiled {
	
	public DNXFunction(ByteBuffer reader) {
		symbolPointer = reader.getInt();
		int size = reader.getShort();
		for(int i = 0; i < size; i++) {
			bytecodeIndicies.add(reader.getInt());
		}
	}
}
