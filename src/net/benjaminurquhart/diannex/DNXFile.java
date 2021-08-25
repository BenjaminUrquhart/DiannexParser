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
import java.util.EnumSet;
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
	private final List<DNXString> externalFunctionNames;

	private final List<DNXFunction> functions;
	private final List<DNXDefinition> definitions;
	
	protected final List<DNXBytecode> bytecode;
	
	private final Map<String, DNXScene> sceneMap;
	private final Map<String, DNXFunction> functionMap;
	private final Map<String, DNXDefinition> definitionMap;
	
	protected final List<DNXBytecode> entryPoints;
	
	private boolean compressed;
	private boolean internalTranslationFile;
	
	protected int version;
	
	protected boolean ready;
	
	public DNXFile() {
		scenes = new ArrayList<>();
		strings = new ArrayList<>();
		bytecode = new ArrayList<>();
		functions = new ArrayList<>();
		definitions = new ArrayList<>();
		translations = new ArrayList<>();
		
		sceneMap = new HashMap<>();
		functionMap = new HashMap<>();
		definitionMap = new HashMap<>();
		
		entryPoints = new ArrayList<>();
		externalFunctionNames = new ArrayList<>();
		
		ready = true;
	}

	public DNXFile(File file) throws IOException {
		this(Files.readAllBytes(file.toPath()));
	}
	
	public DNXFile(byte[] bytes) {
		ByteBuffer reader = ByteBuffer.wrap(bytes);
		reader.order(ByteOrder.BIG_ENDIAN);
		
		try {
			int header = reader.getInt();
			version = header & 0xff;
			if((header >> 8) != 0x444e58) {
				throw new IllegalStateException(String.format(
						"Unexpected DNX header: %c%c%c (v%d)", 
						(header >> 24) & 0xff, 
						(header >> 16) & 0xff, 
						(header >> 8) & 0xff, 
						version
				));
			}
			
			if(version != 2 && version != 3) {
				throw new IllegalStateException("Unsupported DNX version: " + version);
			}
			
			reader.order(ByteOrder.LITTLE_ENDIAN);
			byte flags = reader.get();
			
			compressed = (flags & 1) == 1;
			internalTranslationFile = ((flags >> 1) & 1) == 1;
			
			int size = reader.getInt();
			
			//System.out.println("Input size: " + bytes.length);
			//System.out.println("Size: " + size);
			
			if(compressed) {
				int compressedSize = reader.getInt();
				byte[] zlib = new byte[compressedSize];
				bytes = new byte[size];
				//System.out.println("Compressed size: " + compressedSize);
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
			
			if(version >= 3) {
				
				// Convert byte offsets into indexes like in v2
				
				int offset = 0, index = 0;
				Map<Integer, Integer> indexMapping = new HashMap<>();
				Map<DNXBytecode, Integer> offsetMapping = new HashMap<>();
				bytecode = readUnsizedListOf(DNXBytecode.class, reader);
				
				Set<DNXBytecode.Opcode> toPatch = EnumSet.of(
						DNXBytecode.Opcode.J, 
						DNXBytecode.Opcode.JT, 
						DNXBytecode.Opcode.JF,
						DNXBytecode.Opcode.CHOICEADD,
						DNXBytecode.Opcode.CHOOSEADD,
						DNXBytecode.Opcode.CHOICEADDT,
						DNXBytecode.Opcode.CHOOSEADDT
				);
				
				for(DNXBytecode entry : bytecode) {
					//System.out.println(entry);
					indexMapping.put(offset, index);
					offsetMapping.put(entry, offset);
					offset += entry.getLength();
					index++;
				}
				
				int selfOffset;
				for(DNXBytecode entry : bytecode) {
					if(toPatch.contains(entry.getOpcode())) {
						selfOffset = offsetMapping.get(entry) + entry.getLength();
						entry.setFirstArg(indexMapping.get(selfOffset + entry.getFirstArg()) - indexMapping.get(selfOffset) + 1);
					}
				}
				
				for(DNXScene entry : scenes) {
					for(int i = 0; i < entry.bytecodeIndicies.size(); i++) {
						entry.bytecodeIndicies.set(i, indexMapping.get(entry.bytecodeIndicies.get(i)));
					}
				}
				
				for(DNXFunction entry : functions) {
					for(int i = 0; i < entry.bytecodeIndicies.size(); i++) {
						entry.bytecodeIndicies.set(i, indexMapping.get(entry.bytecodeIndicies.get(i)));
					}
				}
				
				for(DNXDefinition entry : definitions) {
					for(int i = 0, ind; i < entry.bytecodeIndicies.size(); i++) {
						ind = entry.bytecodeIndicies.get(i);
						if(ind != -1) {
							entry.bytecodeIndicies.set(i, indexMapping.get(ind));
						}
					}
				}
			}
			else {
				bytecode = readListOf(DNXBytecode.class, reader);
			}
			
			strings = readListOf(DNXString.class, reader);
			translations = internalTranslationFile ? readListOf(DNXString.class, reader) : new ArrayList<>();
			externalFunctionNames = new ArrayList<>();
			
			if(version >= 3) {
				int listSize = reader.getInt();
				for(int i = 0; i < listSize; i++) {
					externalFunctionNames.add(strings.get(reader.getInt()));
				}
			}
			
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
			@SuppressWarnings("deprecation")
			Consumer<DNXCompiled> copyBytecode = v -> {
				v.instructions = v.entryPoint == null ? new ArrayList<>() : DNXDisassembler.getBytecodeChunk(v, this);
				for(DNXFlag flag : v.flags) {
					flag.valueBytecode = flag.value == null ? new ArrayList<>() : DNXDisassembler.getBytecodeChunk(flag.value, this);
					flag.keyBytecode = flag.key == null ? new ArrayList<>() : DNXDisassembler.getBytecodeChunk(flag.key, this);
				}
			};
			
			scenes.forEach(copyBytecode);
			functions.forEach(copyBytecode);
			definitions.forEach(copyBytecode);
			
			//System.out.println();
			ready = true;
		}
		catch(Exception e) {
			throw new IllegalArgumentException("Invalid DNX file", e);
		}
	}
	
	public synchronized void regenerateBytecodeList() {
		entryPoints.clear();
		bytecode.clear();
		
		Consumer<List<DNXBytecode>> add = list -> {
			if(!list.isEmpty()) {
				entryPoints.add(list.get(0));
			}
			bytecode.addAll(list);
		};
		
		for(DNXDefinition definition : definitions) {
			add.accept(definition.instructions);
		}
		for(DNXFunction function : functions) {
			add.accept(function.instructions);
		}
		for(DNXScene scene : scenes) {
			add.accept(scene.instructions);
			for(DNXFlag flag : scene.flags) {
				add.accept(flag.valueBytecode);
				add.accept(flag.keyBytecode);
			}
		}
		
		Set<DNXBytecode> bytecodeSet = new HashSet<>(bytecode);
		if(bytecodeSet.size() != bytecode.size()) {
			throw new IllegalStateException((bytecode.size() - bytecodeSet.size()) + " duplicate bytecode elements");
		}
		
		// Used for serialization
		int offset = 0;
		for(DNXBytecode entry : bytecode) {
			entry.offset = offset;
			offset += entry.getLength();
		}
	}
	
	public void write(File file) throws IOException {
		compressed = false;
		//System.out.println("Serializing...");
		
		regenerateBytecodeList();
		
		ByteArrayOutputStream rawStream = new ByteArrayOutputStream();
		LittleEndianDataOutputStream stream = new LittleEndianDataOutputStream(rawStream);
		
		List<DNXBytecode> bytecode = new ArrayList<>(this.bytecode.size());
		
		for(DNXBytecode entry : this.bytecode) {
			bytecode.add(entry.clone());
		}
		
		if(version >= 3) {
			int offset = 0, index = 0;
			Map<Integer, Integer> offsetMapping = new HashMap<>();
			Map<DNXBytecode, Integer> bytecodeMapping = new HashMap<>();
			
			for(DNXBytecode entry : bytecode) {
				bytecodeMapping.put(entry, offset);
				offsetMapping.put(index, offset);
				offset += entry.getLength();
				index++;
			}
			
			Set<DNXBytecode.Opcode> toPatch = EnumSet.of(
					DNXBytecode.Opcode.J, 
					DNXBytecode.Opcode.JT, 
					DNXBytecode.Opcode.JF,
					DNXBytecode.Opcode.CHOICEADD,
					DNXBytecode.Opcode.CHOOSEADD,
					DNXBytecode.Opcode.CHOICEADDT,
					DNXBytecode.Opcode.CHOOSEADDT
			);
			
			index = 0;
			for(DNXBytecode entry : bytecode) {
				if(toPatch.contains(entry.getOpcode())) {
					entry.setFirstArg(offsetMapping.get(index + entry.getFirstArg()) - bytecodeMapping.get(entry) - entry.getLength());
				}
				index++;
			}
		}
		
		writeList(stream, scenes);
		writeList(stream, functions);
		writeList(stream, definitions);
		
		if(version >= 3) {
			writeUnsizedList(stream, bytecode);
		}
		else {
			writeList(stream, bytecode);
		}
		
		writeList(stream, strings);
		
		if(internalTranslationFile) {
			writeList(stream, translations);
		}
		
		if(version >= 3) {
			stream.writeInt(externalFunctionNames.size());
			for(DNXString entry : externalFunctionNames) {
				stream.writeInt(strings.indexOf(entry));
			}
		}
		
		rawStream.flush();
		stream.flush();
		LittleEndianDataOutputStream fileStream = new LittleEndianDataOutputStream(new FileOutputStream(file));
		
		fileStream.write(0x44); // D
		fileStream.write(0x4e); // N
		fileStream.write(0x58); // X
		fileStream.write(version);
		
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
		//System.out.printf("Reading list of %s with %d elements\n", clazz, size);
		for(int i = 0; i < size; i++) {
			out.add(constructor.newInstance(reader));
		}
		return out;
	}
	
	private static <T> List<T> readUnsizedListOf(Class<T> clazz, ByteBuffer reader) throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Constructor<T> constructor = clazz.getConstructor(ByteBuffer.class);
		List<T> out = new ArrayList<>();
		int numBytes = reader.getInt();
		int position = reader.position();
		
		while(reader.position() - position < numBytes) {
			out.add(constructor.newInstance(reader));
		}
		return out;
	}
	
	private <T extends IDNXSerializable> void writeList(LittleEndianDataOutputStream stream, List<T> list) throws IOException {
		stream.writeInt(list.size());
		//System.out.printf("Writing list of %s with %d elements\n", list.isEmpty() ? "???" : list.get(0).getClass(), list.size());
		for(T element : list) {
			element.serialize(this, stream);
			stream.flush();
		}
	}
	
	private <T extends IDNXSerializable> void writeUnsizedList(LittleEndianDataOutputStream stream, List<T> list) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		LittleEndianDataOutputStream tmp = new LittleEndianDataOutputStream(buffer);
		
		for(T element : list) {
			element.serialize(this, tmp);
			tmp.flush();
		}
		
		tmp.close();
		byte[] bytes = buffer.toByteArray();
		
		stream.writeInt(bytes.length);
		stream.write(bytes);
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
	
	
	public void addScene(DNXScene scene) {
		addNonNullUnique(scenes, scene);
		rebuildSceneMap();
	}
	
	public void addString(DNXString string) {
		addNonNullUnique(strings, string);
	}
	
	public void addFunction(DNXFunction function) {
		addNonNullUnique(functions, function);
		rebuildFunctionMap();
	}
	
	public void addDefinition(DNXDefinition definition) {
		addNonNullUnique(definitions, definition);
		rebuildDefinitionMap();
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
	
	private <T extends DNXCompiled> void rebuildMap(Map<String, T> map, List<T> list) {
		map.clear();
		for(T t : list) {
			map.put(t.name.get(), t);
		}
	}
	
	public void rebuildSceneMap() {
		rebuildMap(sceneMap, scenes);
	}
	
	public void rebuildFunctionMap() {
		rebuildMap(functionMap, functions);
	}
	
	public void rebuildDefinitionMap() {
		rebuildMap(definitionMap, definitions);
	}
	
	public void rebuildMaps() {
		rebuildSceneMap();
		rebuildFunctionMap();
		rebuildDefinitionMap();
	}
}
