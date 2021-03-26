package net.benjaminurquhart.diannex;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
//import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
//import guru.nidi.graphviz.model.Link;
//import guru.nidi.graphviz.model.LinkTarget;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
//import guru.nidi.graphviz.model.PortNode;

import static guru.nidi.graphviz.model.Factory.*;

public class DNXDisassembler {
	
	private static class Block {
		
		private static int nextId;
		
		public int id;
		public Block left, right;
		public List<DNXBytecode> contents;
		
		public Set<Block> entryPoints;
		
		public Block(Block block) {
			entryPoints = new HashSet<>();
			contents = new ArrayList<>();
			id = nextId++;
			
			if(block != null) {
				entryPoints.add(block);
			}
		}
		
		public boolean isEmpty() {
			return contents.isEmpty() && left == null && right == null;
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
	
	private static final Set<DNXBytecode.Opcode> JUMPS = EnumSet.of(DNXBytecode.Opcode.J, DNXBytecode.Opcode.JT, DNXBytecode.Opcode.JF);
	private static final Set<DNXBytecode.Opcode> EXITS = EnumSet.of(DNXBytecode.Opcode.EXIT, DNXBytecode.Opcode.RET);

	public static List<String> disassemble(DNXBytecode entry, DNXReader reader) {
		return trace(entry, reader).stream().map(b -> b.toString(reader)).collect(Collectors.toList());
	}
	
	public static List<DNXBytecode> trace(DNXBytecode entry, DNXReader reader) {
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
		
		/*
		List<Block> blocks = trace(entry, reader, new ArrayList<>(), new HashMap<>(), start, start);
		traverseTree(blocks.get(0), out);
		*/
		
		return out;
	}
	
	public static BufferedImage renderGraph(DNXBytecode entry, DNXReader reader) {
		int start = reader.bytecode.indexOf(entry);
		List<Block> blocks = trace(entry, reader, new ArrayList<>(), new HashMap<>(), start, start);
		MutableGraph graph = mutGraph("disassembly").setDirected(true);
		System.out.println("Constructing...");
		graph.add(buildGraph(blocks.get(0), new HashMap<>(), reader));
		/*
		for(MutableNode node : graph.rootNodes()) {
			printRecursive(node, 0);
		}*/
		
		System.out.println("Rendering...");
		return Graphviz.fromGraph(graph).render(Format.PNG).toImage();
	}
	/*
	private static void printRecursive(LinkTarget node, int depth) {
		for(int i = 0; i < depth; i++) System.out.print("-");
		String name = "???";
		Collection<Link> links = null;
		if(node instanceof PortNode) {
			node = ((PortNode)node).node();
		}
		if(node instanceof MutableNode) {
			name = ((MutableNode)node).name().toString();
			links = ((MutableNode)node).links();
		}
		System.out.println(name);
		if(links != null) {
			links.forEach(l -> printRecursive(l.to(), depth + 1));
		}
	}
	*/
	private static MutableNode buildGraph(Block block, Map<Block, MutableNode> map, DNXReader reader) {
		if(block.isEmpty()) {
			return null;
		}
		MutableNode node = map.computeIfAbsent(block, b -> mutNode(b.toString(reader))), tmp;
		for(Block entry : block.entryPoints) {
			if(entry.isEmpty()) {
				continue;
			}
			if(!map.containsKey(entry)) {
				buildGraph(entry, map, reader);
			}
			tmp = map.get(entry).addLink(node);
			if(block == entry.left) {
				tmp.add(Color.GREEN);
			}
			else {
				tmp.add(Color.RED);
			}
		}
		if(block.left != null) {
			buildGraph(block.left, map, reader);
		}
		if(block.right != null) {
			buildGraph(block.right, map, reader);
		}
		return node;
	}
	
	@SuppressWarnings("unused")
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
	
	private static List<Block> trace(DNXBytecode entry, DNXReader reader, List<Block> blocks, Map<Integer, Block> jmpMap, int index, int start) {
		if(blocks.isEmpty()) {
			blocks.add(new Block(null));
		}
		Block current = blocks.get(blocks.size() - 1);
		Block block;
		int jmp, size = reader.bytecode.size();
		
		do {
			if(jmpMap.containsKey(index)) {
				block = jmpMap.get(index);
				if(current.left == null) {
					current.left = block;
				}
				else if(current.right == null) {
					current.right = block;
				}
				else {
					throw new IllegalStateException("Blocks cannot have more than 2 children");
				}
				block.entryPoints.add(current);
				current = block;
			}
			current.contents.add(entry);
			
			if(JUMPS.contains(entry.getOpcode())) {
				jmp = index + entry.getFirstArg();
				if(jmpMap.containsKey(jmp)) {
					block = jmpMap.get(jmp);
				}
				else {
					block = new Block(current);
					jmpMap.put(jmp, block);
					blocks.add(block);
				}
				if(entry.getOpcode() == DNXBytecode.Opcode.JF) {
					if(current.right == null) {
						current.right = block;
					}
					else {
						current.left = block;
					}
				}
				else if(current.left == null) {
					current.left = block;
				}
				else {
					current.right = block;
				}
				block = new Block(current);
				blocks.add(block);
				
				if(current.left == null) {
					current.left = block;
				}
				else {
					current.right = block;
				}
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
