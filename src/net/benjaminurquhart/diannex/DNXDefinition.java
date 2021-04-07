package net.benjaminurquhart.diannex;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.google.common.io.LittleEndianDataOutputStream;

public class DNXDefinition extends DNXCompiled {

	private int stringReference;
	private DNXString reference;
	
	public DNXDefinition(ByteBuffer reader) {
		symbolPointer = reader.getInt();
		stringReference = reader.getInt();
		bytecodeIndicies.add(reader.getInt());
	}
	
	public void postProcess(DNXFile reader) {
		super.postProcess(reader);
		
		if(stringReference < 0) {
			int newRef = stringReference ^ (1 << 31);
			reference = reader.getStrings().get(newRef);
		}
		else {
			reference = reader.getTranslationStrings().get(stringReference);
		}
	}
	
	@Override
	public void serialize(DNXFile reader, LittleEndianDataOutputStream buff) throws IOException {
		buff.writeInt(reader.getStrings().indexOf(name));
		int strIndex = reader.getStrings().indexOf(reference);
		int trIndex = reader.getTranslationStrings().indexOf(reference);
		
		if(strIndex < 0 && trIndex < 0) {
			strIndex = reader.getStrings().size();
			reader.addString(reference);
		}
		if(strIndex >= 0) {
			buff.writeInt(strIndex | (1 << 31));
		}
		else if(trIndex >= 0) {
			buff.writeInt(trIndex);
		}
		else {
			throw new IllegalStateException("Floating reference: " + reference);
		}
		buff.writeInt(reader.bytecode.indexOf(entryPoint));
	}
	
	public DNXString getReference() {
		return reference;
	}
	
	public String toString() {
		return String.format("DNXDefinition %s [ref=%s, bytecode=%s]", name.get(), reference, entryPoint);
	}
}
