package net.benjaminurquhart.diannex;

import java.nio.ByteBuffer;

import net.benjaminurquhart.diannex.DNXBytecode.Opcode;

public class DNXScene extends DNXCompiled {

	public DNXScene(ByteBuffer reader) {
		symbolPointer = reader.getInt();
		int size = reader.getShort();
		for(int i = 0; i < size; i++) {
			bytecodeIndicies.add(reader.getInt());
		}
	}
	
	public DNXScene(DNXString name) {
		this.name = name;
		this.instructions.add(new DNXBytecode(null, Opcode.EXIT));
	}
}
