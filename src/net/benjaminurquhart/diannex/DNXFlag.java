package net.benjaminurquhart.diannex;

import java.io.IOException;

import com.google.common.io.LittleEndianDataOutputStream;

public class DNXFlag implements IDNXSerializable {

	public DNXBytecode key;
	public DNXBytecode value;
	
	public DNXFlag(DNXBytecode key, DNXBytecode value) {
		this.value = value;
		this.key = key;
	}
	
	public void serialize(DNXFile reader, LittleEndianDataOutputStream buff) throws IOException {
		buff.writeInt(reader.bytecode.indexOf(value));
		buff.writeInt(reader.bytecode.indexOf(key));
	}
	
	@Override
	public String toString() {
		return toString(null);
	}
	
	public String toString(DNXFile reader) {
		return String.format("DNXFlag [%s, %s]", key.toString(reader), value.toString(reader));
	}
}
