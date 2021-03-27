package net.benjaminurquhart.diannex;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
public class DNXScene extends DNXCompiled {

	public DNXScene(ByteBuffer reader) {
		symbolPointer = reader.getInt();
		int size = reader.getShort();
		for(int i = 0; i < size; i++) {
			bytecodeIndicies.add(reader.getInt());
		}
		
	}
	
	public BufferedImage graph(DNXReader reader) {
		return DNXDisassembler.renderGraph(bytecode.get(0), reader);
	}
}
