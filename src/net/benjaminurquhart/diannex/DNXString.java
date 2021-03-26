package net.benjaminurquhart.diannex;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class DNXString {

	private String value;
	
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
	
	public String get() {
		return value;
	}
	public String getClean() {
		return value.replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
	}
	public String toString() {
		return String.format("DNXString [%s]", getClean());
	}
}
