package net.benjaminurquhart.diannex;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import net.benjaminurquhart.diannex.DNXBytecode.Opcode;


// Graph system taken from UndertaleModTool
// https://github.com/krzys-h/UndertaleModTool/blob/master/UndertaleModLib/Decompiler/Decompiler.cs
public class DNXDisassembler {
	
	private static class Block {
		
		public Integer id;
		public Block left, right;
		public boolean conditionalExit;
		public List<DNXBytecode> contents;
		
		public Set<Block> choices;
		public Set<Block> entryPoints;
		
		public Block(Integer id) {
			this.entryPoints = new HashSet<>();
			this.contents = new ArrayList<>();
			this.id = id;
		}
		
		public String toString() {
			return toString(null);
		}
		
		public String toString(DNXReader reader) {
			StringBuilder sb = new StringBuilder(String.format(
					"Block %d [entry=%s, left=%s, right=%s]",
					id,
					entryPoints.stream().map(b -> String.valueOf(b.id)).collect(Collectors.toList()),
					left == null ? null : left.id,
					right == null ? null : right.id
			));
			for(DNXBytecode entry : contents) {
				sb.append("\n");
				sb.append(entry.toString(reader));
			}
			
			return sb.toString();
		}
		
	}
	
	//private static final Set<DNXBytecode.Opcode> JUMPS = EnumSet.of(DNXBytecode.Opcode.J, DNXBytecode.Opcode.JT, DNXBytecode.Opcode.JF);
	private static final Set<DNXBytecode.Opcode> EXITS = EnumSet.of(DNXBytecode.Opcode.EXIT, DNXBytecode.Opcode.RET);
	
	public static List<String> disassemble(DNXBytecode entry, DNXReader reader) {
		return getBytecodeChunk(entry, reader).stream().map(b -> b.toString(reader)).collect(Collectors.toList());
	}
	
	public static List<DNXBytecode> getBytecodeChunk(DNXBytecode entry, DNXReader reader) {
		int entryIndex = reader.entryPoints.indexOf(entry);
		if(entryIndex == -1) {
			throw new IllegalStateException("Provided bytecode is not an entry point");
		}
		
		List<DNXBytecode> out = new ArrayList<>();
		int index = reader.bytecode.indexOf(entry);
		DNXBytecode next = entryIndex + 1 < reader.entryPoints.size() ? reader.entryPoints.get(entryIndex + 1) : null;
		
		do {
			out.add(entry);
			if(index >= reader.bytecode.size() - 1) {
				break;
			}
			entry = reader.bytecode.get(++index);
		} while(entry != next);
		
		return out;
	}
	
	public static BufferedImage renderGraph(DNXBytecode entry, DNXReader reader) {
		String digraph = convertToDigraph(createBlocks(entry, reader), reader);
		//System.out.println(digraph);
		return Graphviz.fromString(digraph).render(Format.PNG).toImage();
	}
	
	private static Map<Integer, Block> createBlocks(DNXBytecode entryBytecode, DNXReader reader) {
		List<DNXBytecode> code = getBytecodeChunk(entryBytecode, reader);
		Map<DNXBytecode, Integer> addressMap = new HashMap<>();
		Map<Integer, Block> out = new HashMap<>();
		out.put(0, new Block(0));
		Block entry = new Block(null);
		Block exit = new Block(code.size());
		out.put(exit.id, exit);
		
		
		
		Block current = entry;
		
		Function<DNXBytecode, Integer> getAddress = (instr) -> addressMap.computeIfAbsent(instr, $ -> code.indexOf(instr));
		
		BiFunction<Integer, DNXBytecode, Block> getBlock = (addr, instr) -> {
			Block nextBlock = out.get(addr);
			
			if(nextBlock == null) {
				if(addr <= getAddress.apply(instr)) {
					
					Block toSplit = null;
					for(Map.Entry<Integer, Block> e : out.entrySet()) {
						if(e.getKey() < addr && (toSplit == null || e.getKey() > toSplit.id)) {
							toSplit = e.getValue();
						}
					}
					
					List<DNXBytecode> before = new ArrayList<>();
					List<DNXBytecode> after = new ArrayList<>();
					
					for(DNXBytecode inst : toSplit.contents) {
						if(getAddress.apply(inst) < addr) {
							before.add(inst);
						}
						else {
							after.add(inst);
						}
					}
					
					Block block = new Block(addr);
					block.conditionalExit = toSplit.conditionalExit;
					block.right = toSplit.right;
					block.left = toSplit.left;
					toSplit.contents = before;
					block.contents = after;
					
					toSplit.conditionalExit = false;
					toSplit.right = block;
					toSplit.left = block;
					
					out.put(addr, block);
				}
				else {
					out.put(addr, nextBlock = new Block(addr));
				}
			}
			
			return nextBlock;
		};
		
		int addr;
		Block blockAt, nextIfMet, nextIfNotMet;
		for(DNXBytecode instr : code) {
			addr = getAddress.apply(instr);
			blockAt = out.get(addr);
			if(blockAt != null) {
				if(current != null) {
					current.conditionalExit = false;
					current.right = blockAt;
					current.left = blockAt;
					
					blockAt.entryPoints.add(current);
				}
				current = blockAt;
			}
			
			if(current == null) {
				out.put(addr, current = new Block(addr));
			}
			
			current.contents.add(instr);
			
			if(instr.getOpcode().name().matches("CHO(IC|OS)EADDT?")) {
				if(current.choices == null) {
					current.choices = new HashSet<>();
				}
				blockAt = getBlock.apply(addr + instr.getFirstArg(), instr);
				blockAt.entryPoints.add(current);
				current.choices.add(blockAt);
			}
			if(instr.getOpcode() == Opcode.CHOICESEL || instr.getOpcode() == Opcode.CHOOSESEL) {
				current.conditionalExit = true;
				current.right = null;
				current.left = null;
				current = null;
			}
			else if(instr.getOpcode() == Opcode.J) {
				blockAt = getBlock.apply(addr + instr.getFirstArg(), instr);
				
				current.conditionalExit = false;
				current.right = blockAt;
				current.left = blockAt;
				current = null;
			}
			else if(instr.getOpcode() == Opcode.JT || instr.getOpcode() == Opcode.JF) {
				nextIfMet = getBlock.apply(addr + instr.getFirstArg(), instr);
				nextIfNotMet = getBlock.apply(addr + 1, instr);
				
				current.conditionalExit = true;
				current.right = instr.getOpcode() == Opcode.JT ? nextIfNotMet : nextIfMet;
				current.left = instr.getOpcode() == Opcode.JT ? nextIfMet : nextIfNotMet;
				current = null;
			}
			else if(EXITS.contains(instr.getOpcode())) {
				blockAt = getBlock.apply(addr + 1, instr);
				
				current.conditionalExit = false;
				current.right = blockAt;
				current.left = blockAt;
				current = null;
			}
		}
		
		if(current != null) {
			current.right = exit;
			current.left = exit;
		}
		
		// These are sets, no need for the contains check
		for(Block block : out.values()) {
			if(block.left != null) {
				block.left.entryPoints.add(block);
			}
			if(block.right != null) {
				block.right.entryPoints.add(block);
			}
		}
		
		return out;
	}
	
	private static String convertToDigraph(Map<Integer, Block> blocks, DNXReader reader) {
		StringBuilder sb = new StringBuilder("digraph G {\n");
		
		for(Block block : blocks.values()) {
			sb.append("    block_" + block.id + " [label=\"");
			sb.append(String.format(
					"[Block %s, Exit: %s, T: %s, F: %s]\n", 
					block.id, 
					block.conditionalExit, 
					block.left == null ? null : block.left.id, 
					block.right == null ? null : block.right.id
			));
			for(DNXBytecode entry : block.contents) {
				sb.append(entry.toString(reader).replace("\\", "\\\\").replace("\"", "\\\""));
				sb.append("\\n");
			}
			sb.append("\"");
			sb.append(block.id == 0 ? ", color=\"blue\"" : "");
			sb.append(", shape=\"box\"];\n");
		}
		
		sb.append("\n");
		
		for(Block block : blocks.values()) {
			if(block.choices != null) {
				for(Block choice : block.choices) {
					sb.append(String.format("    block_%s -> block_%s [color=\"orange\"];\n", block.id, choice.id));
				}
			}
			else if(block.conditionalExit) {
				if(block.left != null) {
					sb.append(String.format("    block_%s -> block_%s [color=\"green\"];\n", block.id, block.left.id));
				}
				if(block.right != null) {
					sb.append(String.format("    block_%s -> block_%s [color=\"red\"];\n", block.id, block.right.id));
				}
			}
			else if (block.left != null) {
				sb.append(String.format("    block_%s -> block_%s;\n", block.id, block.left.id));
			}
		}
		sb.append("}");
		return sb.toString();
	}
}
