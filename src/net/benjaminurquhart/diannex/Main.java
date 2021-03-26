package net.benjaminurquhart.diannex;

import java.io.File;
import java.nio.file.Files;

public class Main {

	public static void main(String[] args) throws Exception {
		System.setErr(System.out);
		DNXReader reader = new DNXReader(new File("C:\\Users\\benja\\Downloads\\ts-underswap-demo-1.00-windows\\data\\game.dxb"));
		
		File folder = new File("output");
		folder.mkdirs();
		for(DNXScene s : reader.scenes) {
			Files.write(new File(folder, s.getSymbol().getClean() + ".asm").toPath(), s.disassemble(reader).getBytes("utf-8"));
		}
	}
}
