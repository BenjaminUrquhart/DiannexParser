package net.benjaminurquhart.diannex;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
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
import guru.nidi.graphviz.model.Link;
import guru.nidi.graphviz.model.LinkTarget;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
import guru.nidi.graphviz.model.PortNode;

import static guru.nidi.graphviz.model.Factory.*;

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
		MutableGraph graph = mutGraph("disassembly").setDirected(true);
		System.out.println("Constructing...");
		graph.add(buildGraph(blocks.get(0), new HashMap<>()));
		
		for(MutableNode node : graph.rootNodes()) {
			printRecursive(node, 0);
		}
		
		System.out.println("Rendering...");
		return Graphviz.fromGraph(graph).render(Format.PNG).toImage();
	}
	
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
	
	private static MutableNode buildGraph(Block block, Map<Block, MutableNode> map) {
		MutableNode node = map.computeIfAbsent(block, b -> mutNode(String.valueOf(b.id))), tmp;
		if(block.entry != null) {
			tmp = map.get(block.entry).addLink(node);
			if(block == block.entry.left) {
				tmp.add(Color.GREEN);
			}
			else {
				tmp.add(Color.RED);
			}
		}
		if(block.left != null) {
			buildGraph(block.left, map);
		}
		if(block.right != null) {
			buildGraph(block.right, map);
		}
		return node;
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
