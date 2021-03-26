package net.benjaminurquhart.diannex;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DNXDisassembler {
	
	private static class Block {
		
		private static int nextId;
		
		public int id;
		public Block entry, left, right;
		public List<DNXBytecode> contents;
		
		public Block(Block block) {
			contents = new ArrayList<>();
			entry = block;
			id = nextId++;
		}
		
		public String toString() {
			StringBuilder sb = new StringBuilder(String.format(
					"Block %d [entry=%s, left=%s, right=%s]",
					id,
					entry == null ? null: entry.id,
					left == null ? null : left.id,
					right == null ? null : right.id
			));
			for(DNXBytecode entry : contents) {
				sb.append("\n");
				sb.append(entry);
			}
			
			return sb.toString();
		}
		
	}
	
	private static final Set<DNXBytecode.Opcode> JUMPS = EnumSet.of(DNXBytecode.Opcode.J, DNXBytecode.Opcode.JT, DNXBytecode.Opcode.JF);
	private static final Set<DNXBytecode.Opcode> EXITS = EnumSet.of(DNXBytecode.Opcode.EXIT, DNXBytecode.Opcode.RET);

	public static List<String> disassemble(DNXBytecode entry, DNXReader reader) {
		return trace(entry, reader).stream().map(b -> b.toString(reader)).collect(Collectors.toList());
	}
	
	public static List<DNXBytecode> trace(DNXBytecode entry, DNXReader reader) {
		List<Block> blocks = trace(entry, reader, new HashSet<>(), new ArrayList<>(), reader.bytecode.indexOf(entry));
		List<DNXBytecode> out = new ArrayList<>();
		traverseTree(blocks.get(0), out);
		
		return out;
	}
	
	public static BufferedImage renderGraph(DNXBytecode entry, DNXReader reader) {
		List<Block> blocks = trace(entry, reader, new HashSet<>(), new ArrayList<>(), reader.bytecode.indexOf(entry));
		
		return null;
	}
	
	private static void traverseTree(Block block, List<DNXBytecode> out) {
		for(DNXBytecode entry : block.contents) {
			out.add(entry);
		}
		if(block.left != null) {
			traverseTree(block.left, out);
		}
		if(block.right != null) {
			traverseTree(block.right, out);
		}
	}
	
	private static List<Block> trace(DNXBytecode entry, DNXReader reader, Set<DNXBytecode> seen, List<Block> blocks, int index) {
		DNXBytecode jumpEntry;
		if(blocks.isEmpty()) {
			blocks.add(new Block(null));
		}
		Block current = blocks.get(blocks.size() - 1);
		Block block;
		int jmp, size = reader.bytecode.size();
		
		do {
			if(EXITS.contains(entry.getOpcode())) {
				break;
			}
			else if(!seen.add(entry)) {
				return blocks;
			}
			current.contents.add(entry);
			
			if(JUMPS.contains(entry.getOpcode())) {
				jmp = index + entry.getFirstArg();
				block = new Block(current);
				if(entry.getOpcode() == DNXBytecode.Opcode.JF) {
					current.right = block;
				}
				else {
					current.left = block;
				}
				blocks.add(block);
				jumpEntry = reader.bytecode.get(jmp);
				if(EXITS.contains(jumpEntry.getOpcode())) {
					block.contents.add(jumpEntry);
				}
				else {
					trace(jumpEntry, reader, seen, blocks, jmp);
				}
				block = new Block(current);
				if(entry.getOpcode() == DNXBytecode.Opcode.JF) {
					current.left = block;
				}
				else {
					current.right = block;
				}
				blocks.add(block);
				current = block;
			}
			entry = ++index < size ? reader.bytecode.get(index) : null;
		} while(entry != null && !EXITS.contains(entry.getOpcode()));
		
		if(entry != null) {
			current.contents.add(entry);
		}
		
		return blocks;
	}
}
