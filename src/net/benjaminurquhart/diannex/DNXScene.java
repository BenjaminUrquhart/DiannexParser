package net.benjaminurquhart.diannex;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DNXScene {

	private int symbolPointer;
	private List<Integer> bytecodeIndicies;
	
	private DNXString symbol;
	private List<DNXBytecode> bytecode;
	
	public DNXScene(ByteBuffer reader) {
		symbolPointer = reader.getInt();
		
		bytecodeIndicies = new ArrayList<>();
		int size = reader.getShort();
		for(int i = 0; i < size; i++) {
			bytecodeIndicies.add(reader.getInt());
		}
		
		//System.out.printf("DNXScene [symbol=%d, indicies=%s]\n", symbolPointer, bytecodeIndicies);
	}
	
	protected void postProcess(DNXReader reader) {
		symbol = reader.strings.get(symbolPointer);
		bytecode = new ArrayList<>();
		for(int index : bytecodeIndicies) {
			bytecode.add(reader.bytecode.get(index));
		}
		
		//System.out.println(this);
	}
	
	public DNXString getSymbol() {
		return symbol;
	}
	
	public List<DNXBytecode> getBytecode() {
		return Collections.unmodifiableList(bytecode);
	}
	
	public String disassemble(DNXReader reader) {
		List<String> asm = DNXDisassembler.disassemble(bytecode.get(0), reader);
		
		StringBuilder sb = new StringBuilder(symbol.get());
		sb.append(":");
		
		for(String s : asm) {
			sb.append("\n");
			sb.append(s);
		}
		
		return sb.toString();
	}
	
	public BufferedImage graph(DNXReader reader) {
		return DNXDisassembler.renderGraph(bytecode.get(0), reader);
	}
	
	public String toString() {
		return String.format("DNXScene %s [%d script(s)]", symbol, bytecode.size());
	}
}
