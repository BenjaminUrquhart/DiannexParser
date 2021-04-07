package net.benjaminurquhart.diannex;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.Scanner;

import javax.imageio.ImageIO;

public class Main {
	
	private static boolean testWrite = true;

	public static void main(String[] args) throws Exception {
		System.setErr(System.out);
		
		System.out.print("DXB Location: ");
		Scanner sc = new Scanner(System.in);
		DNXFile reader = new DNXFile(new File(sc.nextLine()));
		sc.close();
		
		if(testWrite) {
			reader.write(new File("out.dxb"));
			return;
		}
		
		File folder = new File("output");
		File scenes = new File(folder, "scenes");
		File functions = new File(folder, "functions");
		File definitions = new File(folder, "definitions");
		for(DNXScene scene : reader.getScenes()) {
			writeDisassembly(scene, reader, scenes);
			writeGraph(scene, reader, scenes);
		}
		for(DNXFunction function : reader.getFunctions()) {
			writeDisassembly(function, reader, functions);
			writeGraph(function, reader, functions);
		}
		for(DNXDefinition definition : reader.getDefinitions()) {
			if(!definition.instructions.isEmpty()) {
				writeDisassembly(definition, reader, definitions);
				writeGraph(definition, reader, definitions);
			}
		}
	}
	
	private static void writeDisassembly(DNXCompiled entry, DNXFile reader, File outFolder) throws UnsupportedEncodingException, IOException {
		outFolder.mkdirs();
		System.out.println(entry);
		Files.write(new File(outFolder, entry.name.getClean() + ".asm").toPath(), entry.disassemble(reader).getBytes("utf-8"));
	}
	
	private static void writeGraph(DNXCompiled entry, DNXFile reader, File outFolder) throws IOException {
		outFolder = new File(outFolder, "graphs");
		outFolder.mkdirs();
		ImageIO.write(entry.graph(reader), "png", new File(outFolder, entry.name.getClean() + ".png"));
	}
}
