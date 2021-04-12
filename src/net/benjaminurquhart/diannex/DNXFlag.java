package net.benjaminurquhart.diannex;

import java.io.IOException;
import java.util.List;

import com.google.common.io.LittleEndianDataOutputStream;

public class DNXFlag implements IDNXSerializable {

	// See DNXCompiled#entryPoint
	@Deprecated
	protected DNXBytecode key;
	@Deprecated
	protected DNXBytecode value;
	
	public List<DNXBytecode> keyBytecode, valueBytecode;
	
	public DNXFlag() {}
	
	protected DNXFlag(DNXBytecode key, DNXBytecode value) {
		this.value = value;
		this.key = key;
	}
	
	public DNXFlag(List<DNXBytecode> key, List<DNXBytecode> value) {
		valueBytecode = value;
		keyBytecode = key;
	}
	
	public void serialize(DNXFile reader, LittleEndianDataOutputStream buff) throws IOException {
		buff.writeInt(valueBytecode.isEmpty() ? -1 : reader.bytecode.indexOf(valueBytecode.get(0)));
		buff.writeInt(keyBytecode.isEmpty() ? -1 : reader.bytecode.indexOf(keyBytecode.get(0)));
	}
	
	public String getPretty(DNXFile reader) {
		StringBuilder sb = new StringBuilder();
		try {
			sb.append(reader.getStrings().get(keyBytecode.get(0).getFirstArg()).getClean());
		}
		catch(Exception e) {
			sb.append("???");
		}
		sb.append("=");
		try {
			sb.append(String.valueOf(valueBytecode.get(0).getFirstArg()));
		}
		catch(Exception e) {
			sb.append("???");
		}
		return sb.toString();
	}
	
	private String getValueStr(DNXFile reader) {
		if(valueBytecode != null && !valueBytecode.isEmpty()) {
			return valueBytecode.get(0).toString(reader);
		}
		else if(value != null) {
			return value.toString(reader);
		}
		return null;
	}
	
	private String getKeyStr(DNXFile reader) {
		if(keyBytecode != null && !keyBytecode.isEmpty()) {
			return keyBytecode.get(0).toString(reader);
		}
		else if(key != null) {
			return key.toString(reader);
		}
		return null;
	}
	
	@Override
	public String toString() {
		return toString(null);
	}
	
	public String toString(DNXFile reader) {
		return String.format("DNXFlag [%s, %s]", getKeyStr(reader), getValueStr(reader));
	}
}
