package net.benjaminurquhart.diannex.decompiler;

import java.util.List;
import java.util.Map;

import net.benjaminurquhart.diannex.DNXCompiled;
import net.benjaminurquhart.diannex.DNXDisassembler;
import net.benjaminurquhart.diannex.DNXFile;
import net.benjaminurquhart.diannex.DNXDisassembler.Block;

// TODO
public class DNXDecompiler {

	public static List<String> decompile(DNXCompiled entry, DNXFile file) {
		Map<Integer, Block> blocks = DNXDisassembler.createBlocks(entry);
		return decompileBlock(blocks.get(0), new DecompileContext(file));
	}
	
	private static List<String> decompileBlock(Block block, DecompileContext context) {
		return null;
	}
}
