package net.benjaminurquhart.diannex;

import java.io.File;

public class Main {

	public static void main(String[] args) throws Exception {
		System.setErr(System.out);
		DNXReader reader = new DNXReader(new File("C:\\Users\\benja\\Downloads\\ts-underswap-demo-1.00-windows\\data\\game.dxb"));
		
		System.out.println(reader.sceneMap.get("int_other.ruined5_c").disassemble(reader));
	}
}
