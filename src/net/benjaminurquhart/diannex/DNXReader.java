package net.benjaminurquhart.diannex;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

public class DNXReader {
	
	public final List<DNXScene> scenes;
	public final List<DNXString> strings;
	public final List<DNXString> translations;
	
	public final List<DNXBytecode> bytecode;
	public final List<DNXFunction> functions;
	public final List<DNXDefinition> definitions;
	
	public final Map<String, DNXScene> sceneMap;
	public final Map<String, DNXFunction> functionMap;
	public final Map<String, DNXDefinition> definitionMap;
	
	protected final List<DNXBytecode> entryPoints;

	public DNXReader(File file) throws IOException {
		this(Files.readAllBytes(file.toPath()));
	}
	
	public DNXReader(byte[] bytes) {
		ByteBuffer reader = ByteBuffer.wrap(bytes);
		reader.order(ByteOrder.BIG_ENDIAN);
		
		try {
			int header = reader.getInt();
			if(header != 0x444e5802) {
				throw new IllegalStateException(String.format(
						"Unexpected DNX header; expected DNX v2, got %c%c%c v%d", 
						(header >> 24) & 0xff, 
						(header >> 16) & 0xff, 
						(header >> 8) & 0xff, 
						header & 0xff
				));
			}
			reader.order(ByteOrder.LITTLE_ENDIAN);
			byte flags = reader.get();
			
			boolean compressed = (flags & 1) == 1;
			boolean internalTranslationFile = ((flags >> 1) & 1) == 1;
			
			int size = reader.getInt();
			
			System.out.println("Input size: " + bytes.length);
			System.out.println("Size: " + size);
			if(compressed) {
				int compressedSize = reader.getInt();
				byte[] zlib = new byte[compressedSize];
				bytes = new byte[size];
				System.out.println("Compressed size: " + compressedSize);
				reader.get(zlib);
				
				Inflater inflater = new Inflater(false);
				inflater.setInput(zlib);
				int newSize = inflater.inflate(bytes);
				if(newSize != size) {
					throw new IllegalArgumentException("Invalid compressed data: expected " + size + " bytes, got " + newSize);
				}
				inflater.end();
				
				reader = ByteBuffer.wrap(bytes);
				reader.order(ByteOrder.LITTLE_ENDIAN);
			}
			int pos = reader.position();
			
			scenes = readListOf(DNXScene.class, reader);
			functions = readListOf(DNXFunction.class, reader);
			definitions = readListOf(DNXDefinition.class, reader);
			bytecode = readListOf(DNXBytecode.class, reader);
			strings = readListOf(DNXString.class, reader);
			translations = internalTranslationFile ? readListOf(DNXString.class, reader) : Collections.emptyList();
			
			int read = reader.position() - pos;
			if(read != size) {
				throw new IllegalStateException("Expected " + size + " bytes, read " + read);
			}
			
			Map<String, DNXScene> s = new HashMap<>();
			Map<String, DNXFunction> f = new HashMap<>();
			Map<String, DNXDefinition> d = new HashMap<>();
			
			entryPoints = new ArrayList<>();
			
			scenes.forEach(v -> {v.postProcess(this); s.put(v.getSymbol().get(), v);});
			functions.forEach(v -> {v.postProcess(this); f.put(v.getSymbol().get(), v);});
			definitions.forEach(v -> {v.postProcess(this); d.put(v.getSymbol().get(), v);});
			
			entryPoints.sort((a,b) -> bytecode.indexOf(a) - bytecode.indexOf(b));
			
			sceneMap = Collections.unmodifiableMap(s);
			functionMap = Collections.unmodifiableMap(f);
			definitionMap = Collections.unmodifiableMap(d);

			System.out.println();
		}
		catch(Exception e) {
			throw new IllegalArgumentException("Invalid DNX file", e);
		}
	}
	
	private static <T> List<T> readListOf(Class<T> clazz, ByteBuffer reader) throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Constructor<T> constructor = clazz.getConstructor(ByteBuffer.class);
		List<T> out = new ArrayList<>();
		int size = reader.getInt();
		System.out.printf("Reading list of %s with %d elements\n", clazz, size);
		for(int i = 0; i < size; i++) {
			//System.out.print(i + ": ");
			out.add(constructor.newInstance(reader));
		}
		return Collections.unmodifiableList(out);
	}
}
