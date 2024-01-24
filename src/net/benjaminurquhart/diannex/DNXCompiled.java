package net.benjaminurquhart.diannex;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.io.LittleEndianDataOutputStream;

public abstract class DNXCompiled implements IDNXSerializable {

	public DNXString name;
	protected int symbolPointer;
	protected List<Integer> bytecodeIndicies;
	
	public List<DNXBytecode> instructions;
	public List<DNXFlag> flags;
	
	// Only exists for bytecode list splitting during deserialization.
	// Do not use.
	
	@Deprecated
	protected DNXBytecode entryPoint;
	
	private boolean processed;
	
	protected DNXCompiled() {
		this.flags = new ArrayList<>();
		this.instructions = new ArrayList<>();
		this.bytecodeIndicies = new ArrayList<>();
	}
	
	protected void postProcess(DNXFile reader) {
		DNXBytecode entry;
		List<DNXBytecode> bytecode = new ArrayList<>();
		name = reader.getStrings().get(symbolPointer);
		for(int index : bytecodeIndicies) {
			if(index < 0) continue;
			entry = reader.bytecode.get(index);
			reader.entryPoints.add(entry);
			bytecode.add(entry);
		}
		if(!bytecode.isEmpty()) {
			entryPoint = bytecode.get(0);
			if(bytecode.size() > 1) {
				if(bytecode.size() % 2 == 0) {
					System.out.println(bytecode);
					throw new IllegalStateException(String.format("%s %s has unpaired flags", this.getClass().getSimpleName(), name.getClean()));
				}
				DNXFlag flag;
				for(int i = 1; i < bytecode.size(); i += 2) {
					flag = new DNXFlag(bytecode.get(i+1), bytecode.get(i));
					flags.add(flag);
				}
			}
		}
		processed = true;
	}
	
	@Override
	public int getLength() {
		return flags.size() * 8 + 10;
	}
	
	protected void markProcessed() {
		processed = true;
	}
	
	@Override
	public void serialize(DNXFile reader, LittleEndianDataOutputStream buff) throws IOException {
		buff.writeInt(reader.getStrings().indexOf(name));
		buff.writeShort((short)(flags.size() * 2 + 1));
		buff.writeInt(
				instructions.isEmpty() ? -1 : 
				reader.version >= 3 ? instructions.get(0).offset : 
				reader.bytecode.indexOf(instructions.get(0))
		);
		for(DNXFlag flag : flags) {
			flag.serialize(reader, buff);
		}
	}
	
	public String disassemble(DNXFile reader) {
		List<String> asm = DNXDisassembler.disassemble(this, reader);
		
		StringBuilder sb = new StringBuilder("; ");
		sb.append(name.get());
		if(!flags.isEmpty()) {
			sb.append(" (flags=[");
			
			DNXFlag flag;
			int size = flags.size();
			for(int i = 0; i < size; i++) {
				flag = flags.get(i);
				sb.append(flag.getPretty(reader));
				if(i < size - 1) {
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
	
	public BufferedImage graph(DNXFile reader) {
		return DNXDisassembler.renderGraph(this, reader);
	}
	
	public String toString() {
		int size;
		if(processed) {
			size = instructions.size();
			return String.format(
					"%s %s [%d %s, %d %s]", 
					this.getClass().getSimpleName(), 
					name.getClean(),
					size,
					size == 1 ? "instruction" : "instructions",
					flags.size(),
					flags.size() == 1 ? "flag" : "flags"
			);
		}
		else {
			size = bytecodeIndicies.size();
			return String.format(
					"%s %s [%d bytecode %s]", 
					this.getClass().getSimpleName(), 
					symbolPointer, 
					size, 
					size == 1 ? "entry" : "entries"
			);
		}
		
	}
}
