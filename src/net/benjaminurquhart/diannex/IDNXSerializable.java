package net.benjaminurquhart.diannex;

import java.io.IOException;

import com.google.common.io.LittleEndianDataOutputStream;

public interface IDNXSerializable {

	void serialize(DNXFile file, LittleEndianDataOutputStream stream) throws IOException;
}
