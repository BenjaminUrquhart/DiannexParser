package net.benjaminurquhart.diannex;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.google.common.io.LittleEndianDataOutputStream;

public class DNXString implements IDNXSerializable {

	private String value;
	
	public DNXString(String value) {
		if(value == null) {
			throw new IllegalArgumentException("Cannot create null DNXString");
		}
		this.value = value;
	}
	
	public DNXString(ByteBuffer reader) {
		byte[] buff = new byte[1024], tmp;
		int index = 0;
		byte b;
		
		boolean err = false;
		
		while((b = reader.get()) != 0) {
			buff[index++] = b;
			if(index >= buff.length) {
				tmp = new byte[buff.length * 2];
				System.arraycopy(buff, 0, tmp, 0, index);
				buff = tmp;
			}
			if(!reader.hasRemaining()) {
				err = true;
				break;
			}
		}
		
		byte[] bytes = Arrays.copyOfRange(buff, 0, index);
		try {
			value = new String(bytes, "utf-8");
		}
		catch(Exception e) {
			value = new String(bytes);
		}
		if(err) {
			throw new IllegalStateException("Ran out of bytes while parsing string " + value);
		}
		//System.out.printf("DNXString [%s]\n", value);
	}
	
	@Override
	public int getLength() {
		return value.length() + 1;
	}
	
	@Override
	public void serialize(DNXFile reader, LittleEndianDataOutputStream buff) throws IOException {
		buff.write(value.getBytes("utf-8"));
		buff.write((byte)0);
	}
	
	public String get() {
		return value;
	}
	public String getClean() {
		return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
	}
	public String toString() {
		return String.format("DNXString [%s]", getClean());
	}
}
