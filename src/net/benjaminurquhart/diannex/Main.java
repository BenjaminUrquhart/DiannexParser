package net.benjaminurquhart.diannex;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.Scanner;

import javax.imageio.ImageIO;

public class Main {

	public static void main(String[] args) throws Exception {
		System.setErr(System.out);
		
		System.out.print("DXB Location: ");
		Scanner sc = new Scanner(System.in);
		DNXReader reader = new DNXReader(new File(sc.nextLine()));
		sc.close();
		
		ImageIO.write(reader.sceneMap.get("battles.metta.sb").graph(reader), "png", new File("out.png"));
		
		File folder = new File("output");
		File scenes = new File(folder, "scenes");
		File functions = new File(folder, "functions");
		File definitions = new File(folder, "definitions");
		for(DNXScene scene : reader.scenes) {
			writeDisassembly(scene, reader, scenes);
			writeGraph(scene, reader, scenes);
		}
		for(DNXFunction function : reader.functions) {
			writeDisassembly(function, reader, functions);
			writeGraph(function, reader, functions);
		}
		for(DNXDefinition definition : reader.definitions) {
			if(!definition.getBytecode().isEmpty()) {
				writeDisassembly(definition, reader, definitions);
				writeGraph(definition, reader, definitions);
			}
		}
	}
	
	private static void writeDisassembly(DNXCompiled entry, DNXReader reader, File outFolder) throws UnsupportedEncodingException, IOException {
		outFolder.mkdirs();
		System.out.println(entry);
		Files.write(new File(outFolder, entry.getSymbol().getClean() + ".asm").toPath(), entry.disassemble(reader).getBytes("utf-8"));
	}
	
	private static void writeGraph(DNXCompiled entry, DNXReader reader, File outFolder) throws IOException {
		outFolder = new File(outFolder, "graphs");
		outFolder.mkdirs();
		ImageIO.write(entry.graph(reader), "png", new File(outFolder, entry.getSymbol().getClean() + ".png"));
	}
}
