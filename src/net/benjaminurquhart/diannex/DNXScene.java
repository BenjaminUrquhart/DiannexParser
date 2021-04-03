package net.benjaminurquhart.diannex;

import java.nio.ByteBuffer;

public class DNXScene extends DNXCompiled {

	public DNXScene(ByteBuffer reader) {
		symbolPointer = reader.getInt();
		int size = reader.getShort();
		for(int i = 0; i < size; i++) {
			bytecodeIndicies.add(reader.getInt());
		}
		
	}
}
