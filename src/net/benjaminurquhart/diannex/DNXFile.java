package net.benjaminurquhart.diannex;

import com.google.common.io.LittleEndianDataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class DNXFile {
	
	private final List<DNXScene> scenes;
	private final List<DNXString> strings;
	private final List<DNXString> translations;

	private final List<DNXFunction> functions;
	private final List<DNXDefinition> definitions;
	
	protected final List<DNXBytecode> bytecode;
	
	private final Map<String, DNXScene> sceneMap;
	private final Map<String, DNXFunction> functionMap;
	private final Map<String, DNXDefinition> definitionMap;
	
	protected final List<DNXBytecode> entryPoints;
	
	private boolean compressed;
	private boolean internalTranslationFile;

	public DNXFile(File file) throws IOException {
		this(Files.readAllBytes(file.toPath()));
	}
	
	public DNXFile(byte[] bytes) {
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
			
			compressed = (flags & 1) == 1;
			internalTranslationFile = ((flags >> 1) & 1) == 1;
			
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
			
			// Create object shells
			scenes = readListOf(DNXScene.class, reader);
			functions = readListOf(DNXFunction.class, reader);
			definitions = readListOf(DNXDefinition.class, reader);
			bytecode = readListOf(DNXBytecode.class, reader);
			strings = readListOf(DNXString.class, reader);
			translations = internalTranslationFile ? readListOf(DNXString.class, reader) : new ArrayList<>();
			
			int read = reader.position() - pos;
			if(read != size) {
				throw new IllegalStateException("Expected " + size + " bytes, read " + read);
			}
			
			entryPoints = new ArrayList<>();
			
			sceneMap = new HashMap<>();
			functionMap = new HashMap<>();
			definitionMap = new HashMap<>();
			
			// Populate fields
			scenes.forEach(v -> {v.postProcess(this); sceneMap.put(v.name.get(), v);});
			functions.forEach(v -> {v.postProcess(this); functionMap.put(v.name.get(), v);});
			definitions.forEach(v -> {v.postProcess(this); definitionMap.put(v.name.get(), v);});
			
			entryPoints.sort((a,b) -> bytecode.indexOf(a) - bytecode.indexOf(b));
			
			// Copy bytecode into objects
			Consumer<DNXCompiled> copyBytecode = v -> v.instructions = v.entryPoint == null ? new ArrayList<>() : DNXDisassembler.getBytecodeChunk(v, this);
			
			scenes.forEach(copyBytecode);
			functions.forEach(copyBytecode);
			definitions.forEach(copyBytecode);
			
			System.out.println();
		}
		catch(Exception e) {
			throw new IllegalArgumentException("Invalid DNX file", e);
		}
	}
	
	public void write(File file) throws IOException {
		compressed = false;
		System.out.println("Serializing...");
		List<DNXBytecode> flagBytecode = new ArrayList<>();
		
		bytecode.clear();
		
		for(DNXDefinition definition : definitions) {
			bytecode.addAll(definition.instructions);
		}
		for(DNXFunction function : functions) {
			bytecode.addAll(function.instructions);
		}
		for(DNXScene scene : scenes) {
			bytecode.addAll(scene.instructions);
			for(DNXFlag flag : scene.flags) {
				flagBytecode.add(flag.key);
				flagBytecode.add(flag.value);
			}
		}
		bytecode.addAll(flagBytecode);
		
		Set<DNXBytecode> bytecodeSet = new HashSet<>(bytecode);
		if(bytecodeSet.size() != bytecode.size()) {
			throw new IllegalStateException((bytecode.size() - bytecodeSet.size()) + " duplicate bytecode elements");
		}
		
		ByteArrayOutputStream rawStream = new ByteArrayOutputStream();
		LittleEndianDataOutputStream stream = new LittleEndianDataOutputStream(rawStream);
		
		writeList(stream, scenes);
		writeList(stream, functions);
		writeList(stream, definitions);
		writeList(stream, bytecode);
		writeList(stream, strings);
		
		if(internalTranslationFile) {
			writeList(stream, translations);
		}
		
		rawStream.flush();
		stream.flush();
		LittleEndianDataOutputStream fileStream = new LittleEndianDataOutputStream(new FileOutputStream(file));
		
		fileStream.write(0x44); // D
		fileStream.write(0x4e); // N
		fileStream.write(0x58); // X
		fileStream.write(0x02); // 2
		
		int flags = (compressed ? 1 : 0) | ((internalTranslationFile ? 1 : 0) << 1);
		fileStream.write(flags);
		
		fileStream.writeInt(rawStream.size());
		
		if(compressed) {
			Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, false);
			deflater.setInput(rawStream.toByteArray());
			rawStream.reset();
			
			int compressedSize = 0, read;
			byte[] bytes = new byte[1024];
			while((read = deflater.deflate(bytes)) > 0) {
				compressedSize += read;
				rawStream.write(bytes, 0, read);
				
				//System.out.println(compressedSize + " " + read);
			}
			deflater.end();
			fileStream.writeInt(compressedSize);
		}
		
		fileStream.write(rawStream.toByteArray());
		fileStream.flush();
		
		fileStream.close();
		rawStream.close();
		stream.close();
		
		try {
			new DNXFile(file);
		}
		catch(Exception e) {
			throw new IllegalStateException("Did not serialize file correctly!", e.getCause());
		}
	}
	
	private static <T> List<T> readListOf(Class<T> clazz, ByteBuffer reader) throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Constructor<T> constructor = clazz.getConstructor(ByteBuffer.class);
		List<T> out = new ArrayList<>();
		int size = reader.getInt();
		System.out.printf("Reading list of %s with %d elements\n", clazz, size);
		for(int i = 0; i < size; i++) {
			out.add(constructor.newInstance(reader));
		}
		return out;
	}
	
	private <T extends IDNXSerializable> void writeList(LittleEndianDataOutputStream stream, List<T> list) throws IOException {
		stream.writeInt(list.size());
		System.out.printf("Writing list of %s with %d elements\n", list.isEmpty() ? "???" : list.get(0).getClass(), list.size());
		for(T element : list) {
			element.serialize(this, stream);
			stream.flush();
		}
	}
	
	private <T> void addNonNullUnique(List<T> list, T element) {
		if(element == null) {
			throw new IllegalArgumentException("null value");
		}
		if(list.contains(element)) {
			return;
		}
		list.add(element);
	}
	
	public DNXString newString(String content) {
		if(content == null) {
			throw new IllegalArgumentException("Cannot create null DNXString");
		}
		for(DNXString s : strings) {
			if(s.get().equals(content)) {
				return s;
			}
		}
		DNXString s = new DNXString(content);
		strings.add(s);
		return s;
	}
	
	public DNXString newTranslationString(String content) {
		if(content == null) {
			throw new IllegalArgumentException("Cannot create null DNXString");
		}
		internalTranslationFile = true;
		for(DNXString s : translations) {
			if(s.get().equals(content)) {
				return s;
			}
		}
		DNXString s = new DNXString(content);
		translations.add(s);
		return s;
	}
	
	public List<DNXString> getTranslationStrings() {
		return Collections.unmodifiableList(translations);
	}
	
	public List<DNXDefinition> getDefinitions() {
		return Collections.unmodifiableList(definitions);
	}
	
	public List<DNXFunction> getFunctions() {
		return Collections.unmodifiableList(functions);
	}
	public List<DNXString> getStrings() {
		return Collections.unmodifiableList(strings);
	}
	
	public List<DNXScene> getScenes() {
		return Collections.unmodifiableList(scenes);
	}
	
	public void addTranslationString(DNXString string) {
		addNonNullUnique(translations, string);
		internalTranslationFile = true;
	}
	
	public void addString(DNXString string) {
		addNonNullUnique(strings, string);
	}
	
	public void addFunction(DNXFunction function) {
		addNonNullUnique(functions, function);
	}
	
	public void addDefinition(DNXDefinition definition) {
		addNonNullUnique(definitions, definition);
	}
	
	
	public DNXDefinition definitionByName(String name) {
		return definitionMap.get(name);
	}
	
	public DNXFunction functionByName(String name) {
		return functionMap.get(name);
	}
	
	public DNXScene sceneByName(String name) {
		return sceneMap.get(name);
	}
	
}
