package net.benjaminurquhart.diannex;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.Scanner;

public class Main {

	public static void main(String[] args) throws Exception {
		System.setErr(System.out);
		
		System.out.print("DXB Location: ");
		Scanner sc = new Scanner(System.in);
		DNXReader reader = new DNXReader(new File(sc.nextLine()));
		sc.close();
		
		File folder = new File("output");
		File scenes = new File(folder, "scenes");
		File functions = new File(folder, "functions");
		File definitions = new File(folder, "definitions");
		for(DNXScene scene : reader.scenes) {
			writeDisassembly(scene, reader, scenes);
		}
		for(DNXFunction function : reader.functions) {
			writeDisassembly(function, reader, functions);
		}
		for(DNXDefinition defintion : reader.definitions) {
			if(!defintion.getBytecode().isEmpty()) {
				writeDisassembly(defintion, reader, definitions);
			}
		}
	}
	
	private static void writeDisassembly(DNXCompiled entry, DNXReader reader, File outFolder) throws UnsupportedEncodingException, IOException {
		outFolder.mkdirs();
		Files.write(new File(outFolder, entry.getSymbol().getClean() + ".asm").toPath(), entry.disassemble(reader).getBytes("utf-8"));
	}
}
